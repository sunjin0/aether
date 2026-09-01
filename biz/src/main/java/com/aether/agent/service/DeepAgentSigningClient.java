package com.aether.agent.service;

import com.aether.agent.dto.DeepAgentConfig;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 表示Deep智能体SigningClient。
 */
@Component
public class DeepAgentSigningClient {
    private static final Logger log = LoggerFactory.getLogger(DeepAgentSigningClient.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final DeepAgentConfig config;
    private final RestTemplate restTemplate;

    /**
     * 创建 {@code DeepAgentSigningClient} 实例。
     */
    public DeepAgentSigningClient(DeepAgentConfig config) {
        this.config = config;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 处理signedPost。
     */
    public <T> ResponseEntity<String> signedPost(String path, T body) {
        String url = config.getBaseUrl().replaceAll("/$", "") + path;
        byte[] bodyBytes = JSON.toJSONBytes(body);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = hmacSha256(config.getSharedSecret(), timestamp + "." + new String(bodyBytes, StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Aether-Key-Id", config.getKeyId());
        headers.set("X-Aether-Timestamp", timestamp);
        headers.set("X-Aether-Signature", signature);
        applyTraceContext(headers);

        HttpEntity<byte[]> entity = new HttpEntity<>(bodyBytes, headers);
        String runId = body instanceof java.util.Map ? String.valueOf(((java.util.Map<?, ?>) body).get("run_id")) : null;
        log.info("Deep Agent request: POST endpoint={}, runId={}, bodyBytes={}", path, runId, bodyBytes.length);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Deep Agent returned HTTP " + response.getStatusCodeValue());
        }
        return response;
    }

    /** 将控制面当前 Trace Context 传播到 Deep Agent，不把请求体或密钥写入追踪头。 */
    private void applyTraceContext(HttpHeaders headers) {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.trim().isEmpty()) return;
        String normalized = traceId.replaceAll("[^0-9a-fA-F]", "").toLowerCase();
        if (normalized.length() < 32) normalized = String.format("%032x", normalized.hashCode());
        if (normalized.length() > 32) normalized = normalized.substring(0, 32);
        String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        headers.set("traceparent", "00-" + normalized + "-" + spanId + "-01");
    }

    /**
     * 处理hmacSha256。
     */
    private String hmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC signing failed", e);
        }
    }
}
