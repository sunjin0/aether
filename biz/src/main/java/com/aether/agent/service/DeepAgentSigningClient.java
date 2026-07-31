package com.aether.agent.service;

import com.aether.agent.dto.DeepAgentConfig;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Component
public class DeepAgentSigningClient {
    private static final Logger log = LoggerFactory.getLogger(DeepAgentSigningClient.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final DeepAgentConfig config;
    private final RestTemplate restTemplate;

    public DeepAgentSigningClient(DeepAgentConfig config) {
        this.config = config;
        this.restTemplate = new RestTemplate();
    }

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

        HttpEntity<byte[]> entity = new HttpEntity<>(bodyBytes, headers);
        String runId = body instanceof java.util.Map ? String.valueOf(((java.util.Map<?, ?>) body).get("run_id")) : null;
        log.info("Deep Agent request: POST endpoint={}, runId={}, bodyBytes={}", path, runId, bodyBytes.length);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Deep Agent returned HTTP " + response.getStatusCodeValue());
        }
        return response;
    }

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
