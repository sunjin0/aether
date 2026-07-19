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
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import javax.annotation.PreDestroy;

@Service
public class KnowledgeEmbeddingServiceImpl implements KnowledgeEmbeddingService {

    private static final int TIMEOUT_MS = 30000;
    private static final int EMBEDDING_DIMENSIONS = 1536;
    private final CloseableHttpClient httpClient;
    private final RestTemplate restTemplate;

    public KnowledgeEmbeddingServiceImpl() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(32);
        connectionManager.setDefaultMaxPerRoute(16);
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(TIMEOUT_MS)
                .setSocketTimeout(TIMEOUT_MS)
                .setConnectionRequestTimeout(5000)
                .build();
        this.httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .build();
        this.restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    }

    @Override
    public List<Double> embed(ModelProvider provider, String input) {
        List<List<Double>> embeddings = embedAll(provider, java.util.Collections.singletonList(input));
        return embeddings.get(0);
    }

    @Override
    public List<List<Double>> embedAll(ModelProvider provider, List<String> inputs) {
        if (provider == null || StringUtils.isBlank(provider.getApiBaseUrl())) {
            throw new ServerException(400, "model provider is required for embedding");
        }
        if (inputs == null || inputs.isEmpty() || inputs.stream().anyMatch(StringUtils::isBlank)) {
            throw new ServerException(400, "embedding input is required");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.isNotBlank(provider.getApiKey())) {
            headers.setBearerAuth(AesUtil.decrypt(provider.getApiKey()));
        }

        JSONObject body = new JSONObject();
        body.put("model", provider.getDefaultModel());
        body.put("input", inputs.size() == 1 ? inputs.get(0) : inputs);
        // OpenAI-compatible embedding APIs use the plural field name.
        body.put("dimensions", EMBEDDING_DIMENSIONS);
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
        if (data.size() != inputs.size()) {
            throw new ServerException(500, "embedding response count mismatch");
        }
        List<List<Double>> result = new ArrayList<>(data.size());
        data.sort((left, right) -> Integer.compare(((JSONObject) left).getIntValue("index"),
                ((JSONObject) right).getIntValue("index")));
        for (int itemIndex = 0; itemIndex < data.size(); itemIndex++) {
            JSONArray values = data.getJSONObject(itemIndex).getJSONArray("embedding");
            if (values == null || values.size() != EMBEDDING_DIMENSIONS) {
                throw new ServerException(500, "embedding dimension mismatch");
            }
            List<Double> embedding = new ArrayList<>(values.size());
            for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
                embedding.add(values.getDouble(valueIndex));
            }
            result.add(embedding);
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

    @PreDestroy
    public void close() throws java.io.IOException {
        httpClient.close();
    }

    private String buildEmbeddingUrl(String baseUrl) {
        String normalized = StringUtils.removeEnd(baseUrl, "/");
        if (normalized.endsWith("/v1")) {
            return normalized + "/embeddings";
        }
        return normalized + "/v1/embeddings";
    }
}
