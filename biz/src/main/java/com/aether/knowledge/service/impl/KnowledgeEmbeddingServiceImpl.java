package com.aether.knowledge.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aether.agent.entity.ModelProvider;
import com.aether.knowledge.service.KnowledgeEmbeddingService;
import com.aether.exception.ServerException;
import com.aether.utils.AesUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class KnowledgeEmbeddingServiceImpl implements KnowledgeEmbeddingService {

    private static final int TIMEOUT_MS = 30000;

    @Override
    public List<Double> embed(ModelProvider provider, String input) {
        if (provider == null || StringUtils.isBlank(provider.getApiBaseUrl())) {
            throw new ServerException(400, "model provider is required for embedding");
        }
        if (StringUtils.isBlank(input)) {
            throw new ServerException(400, "embedding input is required");
        }
        RestTemplate restTemplate = createRestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.isNotBlank(provider.getApiKey())) {
            headers.setBearerAuth(AesUtil.decrypt(provider.getApiKey()));
        }

        JSONObject body = new JSONObject();
        body.put("model", provider.getDefaultModel());
        body.put("input", input);
        //设置向量维度
        body.put("dimension", 1536);
        ResponseEntity<String> response = restTemplate.exchange(
                buildEmbeddingUrl(provider.getApiBaseUrl()),
                HttpMethod.POST,
                new HttpEntity<>(body.toJSONString(), headers),
                String.class);
        JSONObject json = JSONObject.parseObject(response.getBody());
        JSONArray data = json.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new ServerException(500, "embedding response is empty");
        }
        JSONArray values = data.getJSONObject(0).getJSONArray("embedding");
        if (values == null || values.size() != 1536) {
            throw new ServerException(500, "embedding dimension mismatch");
        }
        List<Double> result = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            result.add(values.getDouble(i));
        }
        return result;
    }

    @Override
    public String toVectorLiteral(List<Double> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            return null;
        }
        return "[" + embedding.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MS);
        requestFactory.setReadTimeout(TIMEOUT_MS);
        return new RestTemplate(requestFactory);
    }

    private String buildEmbeddingUrl(String baseUrl) {
        String normalized = StringUtils.removeEnd(baseUrl, "/");
        if (normalized.endsWith("/v1")) {
            return normalized + "/embeddings";
        }
        return normalized + "/v1/embeddings";
    }
}
