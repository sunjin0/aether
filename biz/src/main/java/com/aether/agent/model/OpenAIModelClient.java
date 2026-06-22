package com.aether.agent.model;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.ModelProvider;
import com.aether.exception.ServerException;
import com.aether.utils.AesUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI兼容模型客户端。
 */
@Component
public class OpenAIModelClient implements ModelClient {

    private static final int DEFAULT_TIMEOUT_MS = 30000;

    @Override
    public boolean supports(String providerType) {
        return "openai".equalsIgnoreCase(providerType) || "local".equalsIgnoreCase(providerType);
    }

    @Override
    public ModelChatResponse chat(ModelChatRequest request) {
        ModelProvider provider = request.getProvider();
        AgentDefinition agent = request.getAgent();
        try {
            RestTemplate restTemplate = createRestTemplate();
            HttpHeaders headers = createHeaders(provider);
            JSONObject body = createBody(agent, request.getMessages());
            HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    buildChatUrl(provider.getApiBaseUrl()),
                    HttpMethod.POST,
                    entity,
                    String.class);
            return parseResponse(response.getBody(), agent.getModel());
        } catch (ResourceAccessException e) {
            throw new ServerException(503, "模型供应商调用超时");
        } catch (ServerException e) {
            throw e;
        } catch (RestClientException e) {
            throw new ServerException(500, "模型调用失败");
        } catch (Exception e) {
            throw new ServerException(500, "模型响应解析失败");
        }
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(DEFAULT_TIMEOUT_MS);
        requestFactory.setReadTimeout(DEFAULT_TIMEOUT_MS);
        return new RestTemplate(requestFactory);
    }

    private HttpHeaders createHeaders(ModelProvider provider) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.isNotBlank(provider.getApiKey())) {
            headers.setBearerAuth(AesUtil.decrypt(provider.getApiKey()));
        }
        return headers;
    }

    private JSONObject createBody(AgentDefinition agent, List<ModelChatMessage> messages) {
        JSONObject body = new JSONObject();
        body.put("model", StringUtils.defaultIfBlank(agent.getModel(), "gpt-3.5-turbo"));
        body.put("messages", toJsonMessages(messages));
        body.put("temperature", agent.getTemperature());
        body.put("max_tokens", agent.getMaxTokens());
        body.put("stream", false);
        return body;
    }

    private JSONArray toJsonMessages(List<ModelChatMessage> messages) {
        JSONArray array = new JSONArray();
        if (messages == null) {
            return array;
        }
        for (ModelChatMessage message : messages) {
            JSONObject item = new JSONObject();
            item.put("role", message.getRole());
            item.put("content", message.getContent());
            array.add(item);
        }
        return array;
    }

    private String buildChatUrl(String apiBaseUrl) {
        if (StringUtils.isBlank(apiBaseUrl)) {
            throw new ServerException(422, "模型供应商API地址为空");
        }
        String baseUrl = StringUtils.removeEnd(apiBaseUrl, "/");
        if (baseUrl.endsWith("/v1/chat/completions")) {
            return baseUrl;
        }
        return baseUrl + "/v1/chat/completions";
    }

    private ModelChatResponse parseResponse(String responseBody, String defaultModel) {
        if (StringUtils.isBlank(responseBody)) {
            throw new ServerException(500, "模型响应为空");
        }
        JSONObject json = JSONObject.parseObject(responseBody);
        JSONArray choices = json.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new ServerException(500, "模型响应缺少choices");
        }
        JSONObject firstChoice = choices.getJSONObject(0);
        JSONObject message = firstChoice.getJSONObject("message");
        String content = message == null ? null : message.getString("content");
        if (StringUtils.isBlank(content)) {
            throw new ServerException(500, "模型响应内容为空");
        }

        JSONObject usage = json.getJSONObject("usage");
        ModelChatResponse response = new ModelChatResponse();
        response.setContent(content);
        response.setModel(StringUtils.defaultIfBlank(json.getString("model"), defaultModel));
        if (usage != null) {
            response.setPromptTokens(usage.getInteger("prompt_tokens"));
            response.setCompletionTokens(usage.getInteger("completion_tokens"));
            response.setTotalTokens(usage.getInteger("total_tokens"));
        }
        response.setRawResponse(responseBody);
        return response;
    }
}
