package com.aether.agent.model;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.adapter.ModelProviderAdapter;
import com.aether.agent.model.adapter.OpenAIChatAdapter;
import com.aether.agent.observability.ChatLatencyMetrics;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * 模型客户端入口。实际供应商协议由 {@link ModelProviderAdapter} 负责适配。
 */
@Component
public class OpenAIModelClient implements ModelClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAIModelClient.class);
    private static final int DEFAULT_TIMEOUT_MS = 300000;

    private final PooledHttpClient pooledHttpClient;
    private final List<ModelProviderAdapter> adapters;
    private volatile RestTemplate restTemplate;

    /**
     * 创建 {@code OpenAIModelClient} 实例。
     */
    @Autowired
    public OpenAIModelClient(PooledHttpClient pooledHttpClient, List<ModelProviderAdapter> adapters) {
        this.pooledHttpClient = pooledHttpClient;
        this.adapters = adapters;
    }

    /**
     * 测试使用的兼容构造器。
     */
    public OpenAIModelClient(PooledHttpClient pooledHttpClient) {
        this(pooledHttpClient, Collections.<ModelProviderAdapter>singletonList(new OpenAIChatAdapter()));
    }

    /**
     * 处理supports。
     */
    @Override
    public boolean supports(String providerType) {
        for (ModelProviderAdapter adapter : adapters) {
            if (adapter.supports(providerType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 对话当前请求。
     */
    @Override
    public ModelChatResponse chat(ModelChatRequest request) {
        AgentDefinition agent = request.getAgent();
        return getModelChatResponse(request, request.getProvider(), agent == null ? null : agent.getModel());
    }

    /**
     * 对话按Provider。
     */
    @Override
    public ModelChatResponse chatByProvider(ModelChatRequest request) {
        return getModelChatResponse(request, request.getProvider(), request.getModel());
    }

    /**
     * 获取模型对话Response。
     */
    @NotNull
    private ModelChatResponse getModelChatResponse(ModelChatRequest request, ModelProvider provider, String model) {
        ModelProviderAdapter adapter = adapter(provider);
        try {
            RestTemplate restTemplate = createRestTemplate();
            JSONObject body = adapter.body(request, false);
            HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), adapter.headers(request));
            ResponseEntity<String> response = restTemplate.exchange(
                    adapter.chatUrl(request),
                    HttpMethod.POST,
                    entity,
                    String.class);
            String responseBody = response.getBody();
            log.debug("Model response: {}", responseBody);
            return adapter.parseResponse(responseBody, defaultModel(request, model));
        } catch (ResourceAccessException e) {
            throw new ServerException(503, I18nUtils.getMessage("agent.model.provider.timeout"));
        } catch (ServerException e) {
            throw e;
        } catch (RestClientException e) {
            throw new ServerException(500, I18nUtils.getMessage("agent.model.call.failed"));
        } catch (Exception e) {
            log.error("Failed to parse model response", e);
            throw new ServerException(500, I18nUtils.getMessage("agent.model.response.invalid"));
        }
    }

    /**
     * 处理stream。
     */
    @Override
    public ModelStreamResponse stream(ModelChatRequest request, ModelStreamCallback callback) {
        ModelProvider provider = request.getProvider();
        ModelProviderAdapter adapter = adapter(provider);
        try {
            JSONObject body = adapter.body(request, true);
            String url = adapter.chatUrl(request);
            long t0 = System.currentTimeMillis();
            try (PooledHttpClient.HttpStreamResult result = pooledHttpClient.postStream(url, body.toJSONString(), adapter.streamHeaders(request))) {
                long t1 = System.currentTimeMillis();
                log.info("模型连接耗时: requestId={}, {}ms, provider={}, model={}",
                        StringUtils.defaultIfBlank(MDC.get("chatRequestId"), "n/a"), t1 - t0,
                        provider == null ? "n/a" : provider.getName(), defaultModel(request, null));
                ChatLatencyMetrics.record("chat.model_connect", t1 - t0);
                return adapter.parseStream(result.getInputStream(), defaultModel(request, null), callback);
            }
        } catch (ServerException e) {
            throw e;
        } catch (Exception e) {
            log.error("模型流式调用异常, provider={}", provider == null ? "n/a" : provider.getName(), e);
            throw new ServerException(500, I18nUtils.getMessage("agent.model.call.failed"));
        }
    }

    /**
     * 创建RestTemplate。
     */
    private RestTemplate createRestTemplate() {
        if (restTemplate == null) {
            synchronized (this) {
                if (restTemplate == null) {
                    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
                    requestFactory.setConnectTimeout(DEFAULT_TIMEOUT_MS);
                    requestFactory.setReadTimeout(DEFAULT_TIMEOUT_MS);
                    restTemplate = new RestTemplate(requestFactory);
                }
            }
        }
        return restTemplate;
    }

    private ModelProviderAdapter adapter(ModelProvider provider) {
        if (provider == null) {
            throw new ServerException(503, I18nUtils.getMessage("agent.model.provider.not.found"));
        }
        for (ModelProviderAdapter adapter : adapters) {
            if (adapter.supports(provider.getType())) {
                return adapter;
            }
        }
        throw new ServerException(503, I18nUtils.getMessage("agent.model.provider.unsupported"));
    }

    private String defaultModel(ModelChatRequest request, String fallback) {
        if (StringUtils.isNotBlank(fallback)) {
            return fallback;
        }
        AgentDefinition agent = request.getAgent();
        return StringUtils.defaultIfBlank(request.getModel(), agent == null ? null : agent.getModel());
    }

    /**
     * 兼容既有反射测试：OpenAI 响应解析已迁移到适配器。
     */
    @SuppressWarnings("unused")
    private ModelChatResponse parseResponse(String responseBody, String defaultModel) {
        return new OpenAIChatAdapter().parseResponse(responseBody, defaultModel);
    }

    /**
     * 兼容既有反射测试：OpenAI 请求体构建已迁移到适配器。
     */
    @SuppressWarnings("unused")
    private JSONObject createBody(ModelChatRequest request, boolean stream) {
        return new OpenAIChatAdapter().body(request, stream);
    }
}
