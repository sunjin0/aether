package com.aether.governance.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalSecretProviderTest {
    @Test
    void vaultRequiresHttpsEndpoint() {
        assertThrows(IllegalArgumentException.class, () -> new VaultSecretProvider("http://vault:8200", "token"));
    }

    @Test
    void kubernetesRejectsPathTraversalReference() {
        KubernetesSecretProvider provider = new KubernetesSecretProvider("http://kubernetes", "default", "token");
        assertThrows(IllegalArgumentException.class, () -> provider.resolve("../admin", "tenant", "tenant-a"));
    }

    @Test
    void vaultRejectsPathTraversalReference() {
        VaultSecretProvider provider = new VaultSecretProvider("https://vault:8200", "token");
        assertThrows(IllegalArgumentException.class, () -> provider.resolve("secret/../admin", "tenant", "tenant-a"));
    }

    @Test
    void externalProvidersDoNotAllowMutation() {
        KubernetesSecretProvider kubernetes = new KubernetesSecretProvider("http://kubernetes", "default", "token");
        VaultSecretProvider vault = new VaultSecretProvider("https://vault:8200", "token");
        assertThrows(UnsupportedOperationException.class, () -> kubernetes.put("s", "t", "i", java.util.Collections.singletonMap("k", "v")));
        assertThrows(UnsupportedOperationException.class, () -> vault.revoke("s", "t", "i"));
    }

    @Test
    void externalProvidersDoNotReadWithoutTenantScope() {
        KubernetesSecretProvider kubernetes = new KubernetesSecretProvider("http://kubernetes", "default", "token");
        VaultSecretProvider vault = new VaultSecretProvider("https://vault:8200", "token");
        assertTrue(kubernetes.resolve("secret", "connector", "connector-1").isEmpty());
        assertTrue(vault.resolve("secret/path", "connector", "connector-1").isEmpty());
    }
}
