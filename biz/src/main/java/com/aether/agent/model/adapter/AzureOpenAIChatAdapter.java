package com.aether.agent.model.adapter;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatRequest;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.utils.AesUtil;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapter for Azure OpenAI Chat Completions.
 */
@Component
public class AzureOpenAIChatAdapter extends OpenAIChatAdapter {

    private static final String DEFAULT_API_VERSION = "2024-02-15-preview";

    @Override
    public boolean supports(String providerType) {
        return "azure".equalsIgnoreCase(providerType) || "azure-openai".equalsIgnoreCase(providerType);
    }

    @Override
    public String chatUrl(ModelChatRequest request) {
        ModelProvider provider = request.getProvider();
        if (provider == null || StringUtils.isBlank(provider.getApiBaseUrl())) {
            throw new ServerException(422, I18nUtils.getMessage("agent.model.api.base.url.required"));
        }
        String base = StringUtils.removeEnd(provider.getApiBaseUrl(), "/");
        String url = base;
        if (!base.contains("/chat/completions")) {
            if (base.contains("/openai/deployments/")) {
                url = base + "/chat/completions";
            } else {
                String deployment = option(request, "deployment");
                if (StringUtils.isBlank(deployment)) {
                    AgentDefinition agent = request.getAgent() != null ? request.getAgent() : new AgentDefinition();
                    deployment = resolveModel(request, agent);
                }
                url = base + "/openai/deployments/" + deployment + "/chat/completions";
            }
        }
        String apiVersion = StringUtils.defaultIfBlank(option(request, "apiVersion"), DEFAULT_API_VERSION);
        return appendQuery(url, "api-version", apiVersion);
    }

    @Override
    public HttpHeaders headers(ModelChatRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ModelProvider provider = request.getProvider();
        if (provider != null && StringUtils.isNotBlank(provider.getApiKey())) {
            headers.set("api-key", AesUtil.decrypt(provider.getApiKey()));
        }
        return headers;
    }

    @Override
    public Map<String, String> streamHeaders(ModelChatRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        ModelProvider provider = request.getProvider();
        if (provider != null && StringUtils.isNotBlank(provider.getApiKey())) {
            headers.put("api-key", AesUtil.decrypt(provider.getApiKey()));
        }
        return headers;
    }

    @Override
    public JSONObject body(ModelChatRequest request, boolean stream) {
        JSONObject body = super.body(request, stream);
        body.remove("model");
        return body;
    }

    private String option(ModelChatRequest request, String name) {
        if (request.getProviderOptions() == null) return null;
        Object value = request.getProviderOptions().get(name);
        return value == null ? null : String.valueOf(value);
    }

    private String appendQuery(String url, String name, String value) {
        if (url.contains(name + "=")) return url;
        return url + (url.contains("?") ? "&" : "?") + name + "=" + value;
    }
}
