package com.aether.governance.service.impl;

import com.aether.governance.service.SecretProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only Vault KV adapter; mutation remains owned by Vault policy and tooling. */
@Service
@ConditionalOnProperty(name = "aether.secret.provider", havingValue = "vault")
public class VaultSecretProvider implements SecretProvider {
    private final RestTemplate restTemplate = new RestTemplate();
    private final String apiBase;
    private final String token;

    public VaultSecretProvider(
            @Value("${aether.secret.vault.api-base:${AETHER_VAULT_API_BASE:http://vault:8200}}") String apiBase,
            @Value("${aether.secret.vault.token:${AETHER_VAULT_TOKEN:}}") String token) {
        this.apiBase = apiBase.replaceAll("/$", "");
        if (!this.apiBase.startsWith("https://"))
            throw new IllegalArgumentException("Vault Secret Provider 必须使用 HTTPS");
        this.token = token;
    }

    @Override
    public Map<String, String> resolve(String credentialRef, String subjectType, String subjectId) {
        if (credentialRef == null || credentialRef.trim().isEmpty() || token.trim().isEmpty()
                || !"tenant".equals(subjectType) || subjectId == null || subjectId.trim().isEmpty()) return Collections.emptyMap();
        if (!subjectId.matches("[A-Za-z0-9._:-]{1,128}")) throw new IllegalArgumentException("tenantId 格式无效");
        if (!credentialRef.matches("[A-Za-z0-9._/-]{1,256}") || credentialRef.contains(".."))
            throw new IllegalArgumentException("Vault credentialRef 格式无效");
        String path = "/v1/aether/tenants/" + subjectId + "/" + credentialRef.replaceFirst("^/", "");
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Vault-Token", token);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(apiBase + path, HttpMethod.GET,
                new HttpEntity<Void>(headers), new ParameterizedTypeReference<Map<String, Object>>() { });
        Object rawData = response.getBody() == null ? null : response.getBody().get("data");
        if (!(rawData instanceof Map)) return Collections.emptyMap();
        Object nested = ((Map<?, ?>) rawData).get("data");
        Map<?, ?> source = nested instanceof Map ? (Map<?, ?>) nested : (Map<?, ?>) rawData;
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null)
                values.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return values;
    }

    @Override
    public String put(String credentialRef, String subjectType, String subjectId, Map<String, String> values) {
        throw new UnsupportedOperationException("Vault Secret 由 Vault 策略和工具写入");
    }

    @Override
    public void revoke(String credentialRef, String subjectType, String subjectId) {
        throw new UnsupportedOperationException("Vault Secret 由 Vault 策略和工具撤销");
    }
}
