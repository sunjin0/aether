package com.aether.agent.service;

import com.aether.agent.dto.DeepAgentConfig;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConnectorCredentialTokenServiceTest {
    @Test
    void encryptsAndValidatesScopedCredentialWithoutExposingPlaintext() {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setMcpCredentialSecret("connector-secret");
        ConnectorCredentialTokenService service = new ConnectorCredentialTokenService(config);
        Map<String, String> credential = new LinkedHashMap<>();
        credential.put("endpoint", "https://prometheus.internal");
        credential.put("token", "secret-token");

        String token = service.create("run-1", "user-1", "tenant-1", "prom-1",
                Collections.singletonList("prometheus_query"), credential);

        assertFalse(token.contains("secret-token"));
        Map<String, Object> claims = service.decrypt(token);
        service.validate(claims, "tenant-1", "prom-1", "prometheus_query");
        assertEquals("secret-token", ((Map<?, ?>) claims.get("credential")).get("token"));
    }

    @Test
    void rejectsTenantConnectorAndToolScopeMismatch() {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setMcpCredentialSecret("connector-secret");
        ConnectorCredentialTokenService service = new ConnectorCredentialTokenService(config);
        String token = service.create("run-1", "user-1", "tenant-1", "prom-1",
                Collections.singletonList("prometheus_query"), Collections.singletonMap("token", "x"));
        Map<String, Object> claims = service.decrypt(token);

        assertThrows(IllegalArgumentException.class, () -> service.validate(claims, "tenant-2", "prom-1", "prometheus_query"));
        assertThrows(IllegalArgumentException.class, () -> service.validate(claims, "tenant-1", "prom-2", "prometheus_query"));
        assertThrows(IllegalArgumentException.class, () -> service.validate(claims, "tenant-1", "prom-1", "prometheus_write"));
    }
}
