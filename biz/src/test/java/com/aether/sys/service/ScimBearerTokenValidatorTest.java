package com.aether.sys.service;

import com.aether.sys.config.ScimIdentityProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScimBearerTokenValidatorTest {
    @Test
    void validatesOnlyDedicatedEnabledCredential() {
        ScimIdentityProperties properties = new ScimIdentityProperties();
        properties.setEnabled(true);
        properties.setBearerToken("scim-secret");
        ScimBearerTokenValidator validator = new ScimBearerTokenValidator(properties);
        assertTrue(validator.isValid("Bearer scim-secret"));
        assertFalse(validator.isValid("bearer scim-secret"));
        assertFalse(validator.isValid("Bearer wrong"));
    }

    @Test
    void rejectsCredentialWhenScimDisabled() {
        ScimIdentityProperties properties = new ScimIdentityProperties();
        properties.setBearerToken("scim-secret");
        assertFalse(new ScimBearerTokenValidator(properties).isValid("Bearer scim-secret"));
    }

    @Test
    void allowsOnlyConfiguredTenant() {
        ScimIdentityProperties properties = new ScimIdentityProperties();
        properties.setEnabled(true);
        properties.setBearerToken("scim-secret");
        properties.setTenantId("tenant-1");
        ScimBearerTokenValidator validator = new ScimBearerTokenValidator(properties);
        assertTrue(validator.isTenantAllowed("tenant-1"));
        assertFalse(validator.isTenantAllowed("tenant-2"));
        assertFalse(validator.isTenantAllowed(null));
    }

    @Test
    void enabledConfigurationRequiresTenantBinding() {
        ScimIdentityProperties properties = new ScimIdentityProperties();
        properties.setEnabled(true);
        properties.setBearerToken("scim-secret");
        assertThrows(IllegalStateException.class, properties::validate);
        properties.setTenantId("tenant-1");
        properties.validate();
    }
}
