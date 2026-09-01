package com.aether.governance.controller;

import com.aether.governance.service.SecretProvider;
import com.aether.entity.WebResponse;
import com.aether.local.CurrentUser;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;

import static org.mockito.Mockito.*;

class ConnectorCredentialControllerTest {
    @Test
    void rejectsRequestsWithoutTenantAndNeverReadsOrWritesSecret() {
        SecretProvider provider = mock(SecretProvider.class);
        ConnectorCredentialController controller = new ConnectorCredentialController(provider);
        ConnectorCredentialController.CredentialRequest request = new ConnectorCredentialController.CredentialRequest();
        request.credentialRef = "prom-main";
        request.values = Collections.singletonMap("token", "secret");
        CurrentUser.remove();
        WebResponse<String> response = controller.put(request);
        org.junit.jupiter.api.Assertions.assertNotNull(response);
        verifyNoInteractions(provider);
    }

    @Test
    void savesAndRevokesOnlyWithinCurrentTenant() {
        SecretProvider provider = mock(SecretProvider.class);
        ConnectorCredentialController controller = new ConnectorCredentialController(provider);
        HashMap<String, String> user = new HashMap<>();
        user.put("tenantId", "tenant-1");
        CurrentUser.set(user);
        try {
            ConnectorCredentialController.CredentialRequest request = new ConnectorCredentialController.CredentialRequest();
            request.credentialRef = "prom-main";
            request.values = Collections.singletonMap("token", "secret");
            controller.put(request);
            controller.revoke("prom-main");
            verify(provider).put("prom-main", "tenant", "tenant-1", request.values);
            verify(provider).revoke("prom-main", "tenant", "tenant-1");
            verify(provider, never()).resolve(anyString(), anyString(), anyString());
        } finally {
            CurrentUser.remove();
        }
    }

    @Test
    void rejectsInsecureConnectorEndpoint() {
        SecretProvider provider = mock(SecretProvider.class);
        ConnectorCredentialController controller = new ConnectorCredentialController(provider);
        HashMap<String, String> user = new HashMap<>();
        user.put("tenantId", "tenant-1");
        CurrentUser.set(user);
        try {
            ConnectorCredentialController.CredentialRequest request = new ConnectorCredentialController.CredentialRequest();
            request.credentialRef = "prom-main";
            request.values = new HashMap<>();
            request.values.put("endpoint", "http://prometheus.internal");
            request.values.put("token", "secret");
            WebResponse<String> response = controller.put(request);
            org.junit.jupiter.api.Assertions.assertEquals(400, response.getCode());
            verifyNoInteractions(provider);
        } finally {
            CurrentUser.remove();
        }
    }

    @Test
    void rejectsEndpointWithEmbeddedUserInfo() {
        SecretProvider provider = mock(SecretProvider.class);
        ConnectorCredentialController controller = new ConnectorCredentialController(provider);
        HashMap<String, String> user = new HashMap<>();
        user.put("tenantId", "tenant-1");
        CurrentUser.set(user);
        try {
            ConnectorCredentialController.CredentialRequest request = new ConnectorCredentialController.CredentialRequest();
            request.credentialRef = "prom-main";
            request.values = new HashMap<>();
            request.values.put("endpoint", "https://alice:password@prometheus.internal");
            request.values.put("token", "secret");
            WebResponse<String> response = controller.put(request);
            org.junit.jupiter.api.Assertions.assertEquals(400, response.getCode());
            verifyNoInteractions(provider);
        } finally {
            CurrentUser.remove();
        }
    }

    @Test
    void acceptsHttpsEndpointWithoutCredentials() {
        SecretProvider provider = mock(SecretProvider.class);
        ConnectorCredentialController controller = new ConnectorCredentialController(provider);
        HashMap<String, String> user = new HashMap<>();
        user.put("tenantId", "tenant-1");
        CurrentUser.set(user);
        try {
            ConnectorCredentialController.CredentialRequest request = new ConnectorCredentialController.CredentialRequest();
            request.credentialRef = "grafana-prod";
            request.values = new HashMap<>();
            request.values.put("endpoint", "https://grafana.internal/api");
            request.values.put("token", "secret");
            request.values.put("datasourceUid", "prom-main");
            controller.put(request);
            verify(provider).put("grafana-prod", "tenant", "tenant-1", request.values);
        } finally {
            CurrentUser.remove();
        }
    }
}
