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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only Kubernetes Secret adapter; writes remain owned by the cluster secret manager. */
@Service
@ConditionalOnProperty(name = "aether.secret.provider", havingValue = "kubernetes")
public class KubernetesSecretProvider implements SecretProvider {
    private final RestTemplate restTemplate = new RestTemplate();
    private final String apiBase;
    private final String namespace;
    private final String bearerToken;

    public KubernetesSecretProvider(
            @Value("${aether.secret.kubernetes.api-base:https://${KUBERNETES_SERVICE_HOST:kubernetes.default.svc}:443}") String apiBase,
            @Value("${aether.secret.kubernetes.namespace:${AETHER_KUBERNETES_NAMESPACE:default}}") String namespace,
            @Value("${aether.secret.kubernetes.bearer-token:${AETHER_KUBERNETES_BEARER_TOKEN:}}") String bearerToken) {
        this.apiBase = apiBase.replaceAll("/$", "");
        this.namespace = namespace;
        this.bearerToken = bearerToken;
    }

    @Override
    public Map<String, String> resolve(String credentialRef, String subjectType, String subjectId) {
        if (credentialRef == null || credentialRef.trim().isEmpty()
                || !"tenant".equals(subjectType) || subjectId == null || subjectId.trim().isEmpty()) return Collections.emptyMap();
        if (!credentialRef.matches("[a-z0-9]([-a-z0-9]{0,251}[a-z0-9])?"))
            throw new IllegalArgumentException("Kubernetes Secret credentialRef 格式无效");
        if (!subjectId.matches("[a-z0-9]([-a-z0-9]{0,62}[a-z0-9])?"))
            throw new IllegalArgumentException("tenantId 格式无效");
        String secretName = subjectId + "--" + credentialRef;
        if (secretName.length() > 253) throw new IllegalArgumentException("Kubernetes Secret 引用过长");
        String url = apiBase + "/api/v1/namespaces/" + encode(namespace) + "/secrets/" + encode(secretName);
        HttpHeaders headers = new HttpHeaders();
        if (!bearerToken.trim().isEmpty()) headers.setBearerAuth(bearerToken);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<Void>(headers), new ParameterizedTypeReference<Map<String, Object>>() { });
        Object rawData = response.getBody() == null ? null : response.getBody().get("data");
        if (!(rawData instanceof Map)) return Collections.emptyMap();
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawData).entrySet()) {
            if (entry.getKey() != null && entry.getValue() instanceof String)
                values.put(String.valueOf(entry.getKey()), new String(Base64.getDecoder().decode((String) entry.getValue()), StandardCharsets.UTF_8));
        }
        return values;
    }

    @Override
    public String put(String credentialRef, String subjectType, String subjectId, Map<String, String> values) {
        throw new UnsupportedOperationException("Kubernetes Secret 由集群 Secret 管理器写入");
    }

    @Override
    public void revoke(String credentialRef, String subjectType, String subjectId) {
        throw new UnsupportedOperationException("Kubernetes Secret 由集群 Secret 管理器撤销");
    }

    private String encode(String value) {
        return value.replace("/", "%2F");
    }
}
