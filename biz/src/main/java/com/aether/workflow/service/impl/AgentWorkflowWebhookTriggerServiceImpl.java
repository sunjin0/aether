package com.aether.workflow.service.impl;

import com.alibaba.fastjson2.JSON;
import com.aether.workflow.dto.AgentWorkflowBusinessStartDto;
import com.aether.workflow.dto.AgentWorkflowWebhookTriggerDto;
import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.entity.AgentWorkflowWebhookTrigger;
import com.aether.workflow.mapper.AgentWorkflowWebhookTriggerMapper;
import com.aether.workflow.service.AgentWorkflowExecutionService;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.workflow.service.AgentWorkflowWebhookTriggerService;
import com.aether.workflow.vo.AgentWorkflowWebhookTriggerSecretVo;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.sys.entity.ServiceAccount;
import com.aether.sys.service.ServiceAccountService;
import com.aether.utils.AesUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

/**
 * 外部 Webhook 的验签、映射和可靠业务启动。
 */
@Service
public class AgentWorkflowWebhookTriggerServiceImpl
        extends ServiceImpl<AgentWorkflowWebhookTriggerMapper, AgentWorkflowWebhookTrigger>
        implements AgentWorkflowWebhookTriggerService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AgentWorkflowService workflowService;
    private final AgentWorkflowExecutionService executionService;
    private final ServiceAccountService serviceAccountService;
    private final long signatureMaxAgeMillis;

    /**
     * 创建 {@code AgentWorkflowWebhookTriggerServiceImpl} 实例。
     */
    public AgentWorkflowWebhookTriggerServiceImpl(AgentWorkflowService workflowService,
                                                  AgentWorkflowExecutionService executionService,
                                                  ServiceAccountService serviceAccountService,
                                                  @Value("${aether.workflow.webhook.signature-max-age-ms:300000}") long signatureMaxAgeMillis) {
        this.workflowService = workflowService;
        this.executionService = executionService;
        this.serviceAccountService = serviceAccountService;
        this.signatureMaxAgeMillis = Math.max(60000L, signatureMaxAgeMillis);
    }

    /**
     * 创建当前请求。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentWorkflowWebhookTriggerSecretVo create(AgentWorkflowWebhookTriggerDto dto) {
        validate(dto);
        AgentWorkflowWebhookTrigger trigger = new AgentWorkflowWebhookTrigger();
        trigger.setWorkflowId(dto.getWorkflowId());
        trigger.setServiceAccountId(dto.getServiceAccountId());
        trigger.setName(dto.getName());
        trigger.setBusinessType(dto.getBusinessType());
        trigger.setBusinessIdExpression(dto.getBusinessIdExpression());
        trigger.setIdempotencyKeyExpression(dto.getIdempotencyKeyExpression());
        trigger.setVariableMapping(JSON.toJSONString(dto.getVariableMapping() == null ? new LinkedHashMap<String, String>() : dto.getVariableMapping()));
        String secret = "wh_" + randomToken(32);
        trigger.setSigningSecret(AesUtil.encrypt(secret));
        trigger.setEnabled(true);
        save(trigger);
        return secretVo(trigger, secret);
    }

    /**
     * 处理rotateSecret。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentWorkflowWebhookTriggerSecretVo rotateSecret(String id) {
        AgentWorkflowWebhookTrigger trigger = required(id);
        String secret = "wh_" + randomToken(32);
        trigger.setSigningSecret(AesUtil.encrypt(secret));
        updateById(trigger);
        return secretVo(trigger, secret);
    }

    /**
     * 处理setEnabled。
     */
    @Override
    public boolean setEnabled(String id, boolean enabled) {
        AgentWorkflowWebhookTrigger trigger = required(id);
        trigger.setEnabled(enabled);
        return updateById(trigger);
    }

    /**
     * 处理trigger。
     */
    @Override
    @SuppressWarnings("unchecked")
    public AgentWorkflowInstance trigger(String id, String timestamp, String signature, String rawBody, Map<String, String> headers) {
        AgentWorkflowWebhookTrigger trigger = required(id);
        if (!Boolean.TRUE.equals(trigger.getEnabled()))
            throw new ServerException(404, I18nUtils.getMessage("workflow.webhook.not-found-or-disabled"));
        verifySignature(trigger, timestamp, signature, rawBody);
        if (rawBody == null || rawBody.length() > 1024 * 1024)
            throw new ServerException(413, I18nUtils.getMessage("workflow.webhook.request-body.too-large"));
        Object parsed;
        try {
            parsed = JSON.parse(rawBody);
        } catch (Exception ex) {
            throw new ServerException(422, I18nUtils.getMessage("workflow.webhook.request-body.json.invalid"));
        }
        Map<String, Object> body = parsed instanceof Map ? new LinkedHashMap<String, Object>((Map<String, Object>) parsed)
                : Collections.<String, Object>singletonMap("value", parsed);
        try {
            Map<String, String> mapping = StringUtils.isBlank(trigger.getVariableMapping()) ? Collections.<String, String>emptyMap()
                    : JSON.parseObject(trigger.getVariableMapping(), Map.class);
            Map<String, Object> variables = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, String> entry : mapping.entrySet())
                variables.put(entry.getKey(), resolve(entry.getValue(), body, headers));
            String businessId = String.valueOf(resolve(trigger.getBusinessIdExpression(), body, headers));
            String idempotencyKey = String.valueOf(resolve(trigger.getIdempotencyKeyExpression(), body, headers));
            if (StringUtils.isBlank(businessId) || "null".equals(businessId) || StringUtils.isBlank(idempotencyKey) || "null".equals(idempotencyKey))
                throw new ServerException(422, I18nUtils.getMessage("workflow.webhook.business-idempotency-mapping.empty"));
            ServiceAccount account = serviceAccountService.getById(trigger.getServiceAccountId());
            if (account == null || Boolean.TRUE.equals(account.getDeleted()))
                throw new ServerException(422, I18nUtils.getMessage("workflow.webhook.service-account-binding.not-found"));
            serviceAccountService.assertWorkflowStartAllowed(account.getId(), trigger.getWorkflowId());
            AgentWorkflowBusinessStartDto start = new AgentWorkflowBusinessStartDto();
            start.setBusinessType(trigger.getBusinessType());
            start.setBusinessId(businessId);
            start.setIdempotencyKey(idempotencyKey);
            start.setVariables(variables);
            AgentWorkflowInstance instance = executionService.startBusiness(trigger.getWorkflowId(), start, account.getUserId());
            trigger.setLastTriggeredAt(System.currentTimeMillis());
            trigger.setLastErrorMessage(null);
            updateById(trigger);
            return instance;
        } catch (RuntimeException ex) {
            trigger.setLastErrorMessage(StringUtils.abbreviate(ex.getMessage(), 2048));
            updateById(trigger);
            throw ex;
        }
    }

    /**
     * 校验当前请求。
     */
    private void validate(AgentWorkflowWebhookTriggerDto dto) {
        if (dto == null || StringUtils.isBlank(dto.getWorkflowId()) || StringUtils.isBlank(dto.getServiceAccountId())
                || StringUtils.isBlank(dto.getName()) || StringUtils.isBlank(dto.getBusinessType())
                || StringUtils.isBlank(dto.getBusinessIdExpression()) || StringUtils.isBlank(dto.getIdempotencyKeyExpression()))
            throw new ServerException(422, I18nUtils.getMessage("workflow.webhook.configuration.required"));
        AgentWorkflow workflow = workflowService.getById(dto.getWorkflowId());
        if (workflow == null || Boolean.TRUE.equals(workflow.getDeleted()))
            throw new ServerException(422, I18nUtils.getMessage("workflow.webhook.workflow.not-found"));
        ServiceAccount account = serviceAccountService.getById(dto.getServiceAccountId());
        if (account == null || Boolean.TRUE.equals(account.getDeleted()))
            throw new ServerException(422, I18nUtils.getMessage("workflow.webhook.service-account.not-found"));
    }

    /**
     * 处理required。
     */
    private AgentWorkflowWebhookTrigger required(String id) {
        AgentWorkflowWebhookTrigger trigger = getById(id);
        if (trigger == null || Boolean.TRUE.equals(trigger.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("workflow.webhook.not-found"));
        return trigger;
    }

    /**
     * 验证Signature。
     */
    private void verifySignature(AgentWorkflowWebhookTrigger trigger, String timestamp, String signature, String rawBody) {
        long value;
        try {
            value = Long.parseLong(timestamp);
        } catch (Exception ex) {
            throw new ServerException(401, I18nUtils.getMessage("workflow.webhook.timestamp.invalid"));
        }
        if (Math.abs(System.currentTimeMillis() - value) > signatureMaxAgeMillis)
            throw new ServerException(401, I18nUtils.getMessage("workflow.webhook.timestamp.expired"));
        String secret = AesUtil.decrypt(trigger.getSigningSecret());
        String expected = "sha256=" + hmac(secret, timestamp + "." + StringUtils.defaultString(rawBody));
        if (StringUtils.isBlank(signature) || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8)))
            throw new ServerException(401, I18nUtils.getMessage("workflow.webhook.signature.invalid"));
    }

    /**
     * 处理hmac。
     */
    private String hmac(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new ServerException(500, I18nUtils.getMessage("workflow.webhook.signature.calculation.failed"));
        }
    }

    /**
     * 解析当前请求。
     */
    private Object resolve(String expression, Map<String, Object> body, Map<String, String> headers) {
        if (expression == null) return null;
        if ("$body".equals(expression)) return body;
        if (expression.startsWith("$body.")) return path(body, expression.substring(6));
        if (expression.startsWith("$header.")) {
            String wanted = expression.substring(8);
            for (Map.Entry<String, String> header : headers.entrySet())
                if (wanted.equalsIgnoreCase(header.getKey())) return header.getValue();
            return null;
        }
        return expression;
    }

    /**
     * 处理path。
     */
    @SuppressWarnings("unchecked")
    private Object path(Object value, String path) {
        Object current = value;
        for (String key : path.split("\\.")) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(key);
        }
        return current;
    }

    /**
     * 处理secretVO。
     */
    private AgentWorkflowWebhookTriggerSecretVo secretVo(AgentWorkflowWebhookTrigger trigger, String secret) {
        AgentWorkflowWebhookTriggerSecretVo value = new AgentWorkflowWebhookTriggerSecretVo();
        value.setId(trigger.getId());
        value.setWebhookUrl("/api/agent/workflow/webhook/" + trigger.getId());
        value.setSigningSecret(secret);
        return value;
    }

    /**
     * 处理random令牌。
     */
    private String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
