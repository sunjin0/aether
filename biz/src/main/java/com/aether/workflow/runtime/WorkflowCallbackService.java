package com.aether.workflow.runtime;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONArray;
import com.aether.workflow.config.WorkflowCallbackProperties;
import com.aether.workflow.entity.AgentWorkflowCallbackDelivery;
import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.entity.AgentWorkflowVersion;
import com.aether.workflow.service.AgentWorkflowCallbackDeliveryService;
import com.aether.workflow.service.AgentWorkflowVersionService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 负责可靠投递工作流的终态通知；业务回调失败不会回滚已经完成的流程。 */
@Service
public class WorkflowCallbackService {
    private static final Logger log = LoggerFactory.getLogger(WorkflowCallbackService.class);
    private static final int MAX_RESPONSE_BODY_LENGTH = 2048;

    private final AgentWorkflowCallbackDeliveryService deliveryService;
    private final WorkflowCallbackProperties properties;
    private final TaskExecutor callbackExecutor;
    private final AgentWorkflowVersionService versionService;
    private final WorkflowSensitiveDataSanitizer sensitiveDataSanitizer;

    public WorkflowCallbackService(AgentWorkflowCallbackDeliveryService deliveryService,
                                   WorkflowCallbackProperties properties,
                                   @Qualifier("asyncPoolTaskExecutor") TaskExecutor callbackExecutor,
                                   AgentWorkflowVersionService versionService, WorkflowSensitiveDataSanitizer sensitiveDataSanitizer) {
        this.deliveryService = deliveryService;
        this.properties = properties;
        this.callbackExecutor = callbackExecutor;
        this.versionService = versionService;
        this.sensitiveDataSanitizer = sensitiveDataSanitizer;
    }

    /** 在创建业务实例时校验地址，阻止任意地址回调。 */
    public void validateCallbackUrl(String callbackUrl) {
        if (StringUtils.isBlank(callbackUrl)) return;
        if (!properties.isEnabled()) throw new IllegalArgumentException("工作流业务回调未启用");
        if (StringUtils.isBlank(properties.getSigningSecret())) throw new IllegalArgumentException("工作流业务回调签名密钥未配置");
        try {
            URI uri = URI.create(callbackUrl);
            String host = uri.getHost();
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))
                throw new IllegalArgumentException("回调地址仅支持 HTTP 或 HTTPS");
            List<String> allowedHosts = properties.getAllowedHosts() == null ? Collections.<String>emptyList() : properties.getAllowedHosts();
            if (StringUtils.isBlank(host) || !allowedHosts.stream().anyMatch(item -> host.equalsIgnoreCase(item)))
                throw new IllegalArgumentException("回调地址主机不在 aether.workflow.callback.allowed-hosts 白名单中");
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("回调地址格式无效");
        }
    }

    /** 保存终态事件。事务提交后异步投递，避免将未提交数据通知到外部系统。 */
    public void recordTerminal(final AgentWorkflowInstance instance) {
        if (instance == null || StringUtils.isBlank(instance.getCallbackUrl())) return;
        String eventType = eventType(instance.getStatus());
        if (eventType == null) return;
        AgentWorkflowCallbackDelivery existing = deliveryService.getOne(Wrappers.lambdaQuery(AgentWorkflowCallbackDelivery.class)
                .eq(AgentWorkflowCallbackDelivery::getInstanceId, instance.getId())
                .eq(AgentWorkflowCallbackDelivery::getEventType, eventType));
        if (existing != null) return;
        AgentWorkflowCallbackDelivery delivery = new AgentWorkflowCallbackDelivery();
        delivery.setInstanceId(instance.getId());
        delivery.setEventType(eventType); delivery.setCallbackUrl(instance.getCallbackUrl());
        delivery.setStatus("PENDING"); delivery.setAttemptCount(0); delivery.setNextAttemptAt(System.currentTimeMillis());
        delivery.setPayload(buildPayload(instance, eventType));
        try {
            deliveryService.save(delivery);
        } catch (DuplicateKeyException ignored) {
            return;
        }
        Runnable task = () -> callbackExecutor.execute(() -> dispatch(delivery.getId()));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { task.run(); }
            });
        } else {
            task.run();
        }
    }

    private void dispatch(String deliveryId) {
        AgentWorkflowCallbackDelivery delivery = deliveryService.getById(deliveryId);
        if (delivery == null || "DELIVERED".equals(delivery.getStatus()) || "FAILED".equals(delivery.getStatus())) return;
        if (!properties.isEnabled()) return;
        long now = System.currentTimeMillis();
        boolean claimed = deliveryService.update(new LambdaUpdateWrapper<AgentWorkflowCallbackDelivery>()
                .set(AgentWorkflowCallbackDelivery::getStatus, "DELIVERING")
                .set(AgentWorkflowCallbackDelivery::getNextAttemptAt, now + Math.max(30000L,
                        (long) properties.getConnectTimeoutMs() + properties.getReadTimeoutMs() + 5000L))
                .eq(AgentWorkflowCallbackDelivery::getId, deliveryId)
                .in(AgentWorkflowCallbackDelivery::getStatus, "PENDING", "RETRYING"));
        if (!claimed) return;
        delivery = deliveryService.getById(deliveryId);
        try {
            validateCallbackUrl(delivery.getCallbackUrl());
        } catch (IllegalArgumentException ex) {
            delivery.setStatus("FAILED"); delivery.setErrorMessage(ex.getMessage()); deliveryService.updateById(delivery);
            return;
        }
        int nextAttempt = (delivery.getAttemptCount() == null ? 0 : delivery.getAttemptCount()) + 1;
        delivery.setAttemptCount(nextAttempt);
        try {
            ResponseEntity<String> response = restTemplate().postForEntity(delivery.getCallbackUrl(), request(delivery), String.class);
            delivery.setResponseStatus(response.getStatusCodeValue());
            delivery.setResponseBody(shorten(response.getBody()));
            if (response.getStatusCode().is2xxSuccessful()) {
                delivery.setStatus("DELIVERED"); delivery.setDeliveredAt(System.currentTimeMillis()); delivery.setNextAttemptAt(null);
                delivery.setErrorMessage(null);
            } else if (isRetryableStatus(response.getStatusCodeValue())) {
                retryOrFail(delivery, "业务回调返回 HTTP " + response.getStatusCodeValue());
            } else {
                permanentFailure(delivery, "业务回调返回不可重试的 HTTP " + response.getStatusCodeValue());
            }
        } catch (RestClientException ex) {
            retryOrFail(delivery, "业务回调请求失败: " + StringUtils.defaultIfBlank(ex.getMessage(), ex.getClass().getSimpleName()));
        } catch (Exception ex) {
            retryOrFail(delivery, "业务回调处理失败: " + ex.getClass().getSimpleName());
        }
        deliveryService.updateById(delivery);
    }

    /** 补偿进程重启、短暂网络故障等未投递记录。 */
    @Scheduled(fixedDelayString = "${aether.workflow.callback.retry-interval-ms:60000}", initialDelay = 60000L)
    public void retryPending() {
        if (!properties.isEnabled()) return;
        long now = System.currentTimeMillis();
        List<AgentWorkflowCallbackDelivery> pending = deliveryService.list(Wrappers.lambdaQuery(AgentWorkflowCallbackDelivery.class)
                .in(AgentWorkflowCallbackDelivery::getStatus, "PENDING", "RETRYING", "DELIVERING")
                .le(AgentWorkflowCallbackDelivery::getNextAttemptAt, now)
                .lt(AgentWorkflowCallbackDelivery::getAttemptCount, Math.max(1, properties.getMaxAttempts()))
                .orderByAsc(AgentWorkflowCallbackDelivery::getCreatedAt).last("LIMIT 100"));
        for (AgentWorkflowCallbackDelivery delivery : pending) callbackExecutor.execute(() -> dispatch(delivery.getId()));
    }

    /** 将达到最大重试次数的投递重新置为待发送，由人工在修复业务端后触发。 */
    public boolean retryFailed(String deliveryId) {
        AgentWorkflowCallbackDelivery delivery = deliveryService.getById(deliveryId);
        if (delivery == null || !"FAILED".equals(delivery.getStatus())) return false;
        boolean reset = deliveryService.update(new LambdaUpdateWrapper<AgentWorkflowCallbackDelivery>()
                .set(AgentWorkflowCallbackDelivery::getStatus, "PENDING")
                .set(AgentWorkflowCallbackDelivery::getAttemptCount, 0)
                .set(AgentWorkflowCallbackDelivery::getErrorMessage, null)
                .set(AgentWorkflowCallbackDelivery::getNextAttemptAt, System.currentTimeMillis())
                .eq(AgentWorkflowCallbackDelivery::getId, deliveryId)
                .eq(AgentWorkflowCallbackDelivery::getStatus, "FAILED"));
        if (reset) callbackExecutor.execute(() -> dispatch(deliveryId));
        return reset;
    }

    private HttpEntity<String> request(AgentWorkflowCallbackDelivery delivery) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Aether-Workflow-Event", delivery.getEventType());
        headers.set("X-Aether-Workflow-Delivery-Id", delivery.getId());
        headers.set("X-Aether-Workflow-Timestamp", timestamp);
        headers.set("X-Aether-Workflow-Signature", "sha256=" + sign(timestamp + "." + delivery.getPayload()));
        return new HttpEntity<String>(delivery.getPayload(), headers);
    }

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(100, properties.getConnectTimeoutMs()));
        factory.setReadTimeout(Math.max(100, properties.getReadTimeoutMs()));
        RestTemplate template = new RestTemplate(factory);
        // 让调用方读取 4xx/5xx 的状态码与响应体，按幂等语义决定是否重试。
        template.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override public boolean hasError(org.springframework.http.client.ClientHttpResponse response) { return false; }
        });
        return template;
    }

    private void retryOrFail(AgentWorkflowCallbackDelivery delivery, String error) {
        delivery.setErrorMessage(shorten(error));
        int attempts = delivery.getAttemptCount() == null ? 0 : delivery.getAttemptCount();
        if (attempts >= Math.max(1, properties.getMaxAttempts())) {
            delivery.setStatus("FAILED"); delivery.setNextAttemptAt(null);
        } else {
            delivery.setStatus("RETRYING");
            long delay = Math.min(3600000L, 1000L << Math.min(10, attempts));
            delivery.setNextAttemptAt(System.currentTimeMillis() + delay);
        }
    }

    private boolean isRetryableStatus(int status) {
        return status == 408 || status == 425 || status == 429 || status >= 500;
    }

    private void permanentFailure(AgentWorkflowCallbackDelivery delivery, String error) {
        delivery.setStatus("FAILED");
        delivery.setNextAttemptAt(null);
        delivery.setErrorMessage(shorten(error));
    }

    private String eventType(String status) {
        if ("COMPLETED".equals(status)) return "workflow.completed";
        if ("FAILED".equals(status)) return "workflow.failed";
        if ("TERMINATED".equals(status)) return "workflow.terminated";
        if ("TIMED_OUT".equals(status)) return "workflow.timed_out";
        return null;
    }

    private String sign(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getSigningSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成工作流回调签名", ex);
        }
    }

    private String buildPayload(AgentWorkflowInstance instance, String eventType) {
        JSONObject payload = new JSONObject();
        payload.put("eventType", eventType); payload.put("instanceId", instance.getId());
        payload.put("workflowId", instance.getWorkflowId()); payload.put("workflowVersionId", instance.getWorkflowVersionId());
        payload.put("businessType", instance.getBusinessType()); payload.put("businessId", instance.getBusinessId());
        payload.put("idempotencyKey", instance.getIdempotencyKey()); payload.put("status", instance.getStatus());
        payload.put("outputs", outputVariables(instance));
        payload.put("errorMessage", instance.getErrorMessage()); payload.put("startedAt", instance.getStartedAt());
        payload.put("completedAt", instance.getCompletedAt());
        return sensitiveDataSanitizer.sanitizeJson(payload.toJSONString());
    }

    /** 仅按发布版本声明的 outputSchema 暴露变量，内部上下文不会随回调泄漏。 */
    private JSONObject outputVariables(AgentWorkflowInstance instance) {
        JSONObject outputs = new JSONObject();
        if (StringUtils.isBlank(instance.getVariables())) return outputs;
        AgentWorkflowVersion version = versionService.getById(instance.getWorkflowVersionId());
        if (version == null || StringUtils.isBlank(version.getOutputSchema())) return outputs;
        try {
            JSONObject variables = JSONObject.parseObject(instance.getVariables());
            for (Object item : JSONArray.parseArray(version.getOutputSchema())) {
                if (!(item instanceof JSONObject)) continue;
                String name = ((JSONObject) item).getString("name");
                if (StringUtils.isNotBlank(name) && variables.containsKey(name)) outputs.put(name, variables.get(name));
            }
        } catch (Exception ex) {
            log.warn("工作流输出契约解析失败，instanceId={}", instance.getId());
        }
        return outputs;
    }

    private String shorten(String value) {
        if (value == null) return null;
        return value.length() <= MAX_RESPONSE_BODY_LENGTH ? value : value.substring(0, MAX_RESPONSE_BODY_LENGTH);
    }
}
