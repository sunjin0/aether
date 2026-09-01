package com.aether.sys.service;

import com.aether.sys.config.ScimIdentityProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Dedicated SCIM credential check; comparisons are constant-time. */
@Component
public class ScimBearerTokenValidator {
    private final ScimIdentityProperties properties;

    public ScimBearerTokenValidator(ScimIdentityProperties properties) {
        this.properties = properties;
    }

    public boolean isValid(String authorization) {
        if (!properties.isEnabled() || authorization == null || !authorization.startsWith("Bearer ")) return false;
        String configured = properties.getBearerToken();
        String supplied = authorization.substring("Bearer ".length());
        if (configured == null || supplied.isEmpty()) return false;
        return MessageDigest.isEqual(configured.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isTenantAllowed(String tenantId) {
        return tenantId != null && properties.getTenantId() != null
                && MessageDigest.isEqual(properties.getTenantId().getBytes(StandardCharsets.UTF_8),
                tenantId.getBytes(StandardCharsets.UTF_8));
    }
}
