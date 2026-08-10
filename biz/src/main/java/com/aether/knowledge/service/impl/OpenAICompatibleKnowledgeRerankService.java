package com.aether.knowledge.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aether.agent.entity.ModelProvider;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import com.aether.knowledge.service.KnowledgeRerankService;
import com.aether.utils.AesUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for the widely used OpenAI-compatible rerank shape:
 * {model, query, documents, top_n} -> {data: [{index, relevance_score}]}.
 */
@Service
public class OpenAICompatibleKnowledgeRerankService implements KnowledgeRerankService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public List<KnowledgeDocumentChunk> rerank(ModelProvider provider, String model, String query,
                                                List<KnowledgeDocumentChunk> candidates, int topN) {
        if (provider == null || StringUtils.isBlank(provider.getApiBaseUrl()) || candidates == null || candidates.isEmpty()) {
            return candidates;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.isNotBlank(provider.getApiKey())) {
            headers.setBearerAuth(AesUtil.decrypt(provider.getApiKey()));
        }
        JSONObject body = new JSONObject();
        body.put("model", StringUtils.defaultIfBlank(model, provider.getDefaultModel()));
        body.put("query", query);
        JSONArray documents = new JSONArray();
        for (KnowledgeDocumentChunk candidate : candidates) {
            documents.add(candidate.getContent());
        }
        body.put("documents", documents);
        body.put("top_n", Math.min(Math.max(1, topN), candidates.size()));
        String response = postRerank(provider.getApiBaseUrl(), body.toJSONString(), headers);
        return applyScores(response, candidates, topN);
    }

    /**
     * OpenAI-compatible gateways normally expose /v1/rerank, while several local
     * rerank services expose /rerank directly. Retry only a 404 on the alternate
     * path so authentication and request errors remain visible to the caller.
     */
    private String postRerank(String baseUrl, String body, HttpHeaders headers) {
        String[] urls = buildRerankUrls(baseUrl);
        try {
            return post(urls[0], body, headers);
        } catch (HttpClientErrorException.NotFound notFound) {
            if (urls.length < 2) throw notFound;
            return post(urls[1], body, headers);
        }
    }

    private String post(String url, String body, HttpHeaders headers) {
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class).getBody();
    }

    private List<KnowledgeDocumentChunk> applyScores(String response, List<KnowledgeDocumentChunk> candidates, int topN) {
        JSONObject json = JSONObject.parseObject(response);
        JSONArray data = json == null ? null : json.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new ServerException(502, I18nUtils.getMessage("knowledge.rerank.response.scores.empty"));
        }
        Map<Integer, Double> scores = new HashMap<>();
        for (int i = 0; i < data.size(); i++) {
            JSONObject item = data.getJSONObject(i);
            Integer index = item.getInteger("index");
            Double score = item.getDouble("relevance_score");
            if (index != null && score != null && index >= 0 && index < candidates.size()) {
                scores.put(index, score);
            }
        }
        if (scores.isEmpty()) {
            throw new ServerException(502, I18nUtils.getMessage("knowledge.rerank.response.scores.unusable"));
        }
        List<KnowledgeDocumentChunk> ranked = new ArrayList<>();
        for (Map.Entry<Integer, Double> entry : scores.entrySet()) {
            KnowledgeDocumentChunk candidate = candidates.get(entry.getKey());
            candidate.setRetrievalScore(entry.getValue());
            ranked.add(candidate);
        }
        ranked.sort(Comparator.comparing(KnowledgeDocumentChunk::getRetrievalScore).reversed());
        return ranked.subList(0, Math.min(Math.max(1, topN), ranked.size()));
    }

    private String[] buildRerankUrls(String baseUrl) {
        String normalized = StringUtils.removeEnd(baseUrl, "/");
        // Allow the provider to specify an exact endpoint when it differs from
        // either conventional base path.
        if (normalized.endsWith("/rerank")) return new String[]{normalized};
        if (normalized.endsWith("/v1")) {
            return new String[]{normalized + "/rerank", StringUtils.removeEnd(normalized, "/v1") + "/rerank"};
        }
        return new String[]{normalized + "/v1/rerank", normalized + "/rerank"};
    }
}
