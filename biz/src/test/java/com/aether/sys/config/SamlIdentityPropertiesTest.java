package com.aether.sys.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SamlIdentityPropertiesTest {
    @Test
    void disabledConfigurationIsAllowed() {
        assertDoesNotThrow(() -> new SamlIdentityProperties().validate());
    }

    @Test
    void enabledConfigurationRequiresAllSecureEndpoints() {
        SamlIdentityProperties properties = new SamlIdentityProperties();
        properties.setEnabled(true);
        properties.setEntityId("https://sp.example/aether");
        properties.setMetadataUri("http://idp.example/metadata");
        properties.setIdpEntityId("idp");
        properties.setCertificate("CERTIFICATE");
        properties.setRedirectUri("https://sp.example/aether/saml/callback");
        properties.setSsoUrl("https://idp.example/saml/sso");
        assertThrows(IllegalStateException.class, properties::validate);

        properties.setMetadataUri("https://idp.example/metadata");
        assertDoesNotThrow(properties::validate);
    }

    @Test
    void enabledConfigurationRequiresSsoEndpoint() {
        SamlIdentityProperties properties = new SamlIdentityProperties();
        properties.setEnabled(true);
        properties.setEntityId("https://sp.example/aether");
        properties.setMetadataUri("https://idp.example/metadata");
        properties.setIdpEntityId("idp");
        properties.setCertificate("CERTIFICATE");
        properties.setRedirectUri("https://sp.example/aether/saml/callback");
        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void metadataDrivenModeDoesNotRequireManualIdpFields() {
        SamlIdentityProperties properties = new SamlIdentityProperties();
        properties.setEnabled(true);
        properties.setMetadataDriven(true);
        properties.setEntityId("https://sp.example/aether");
        properties.setMetadataUri("https://idp.example/metadata");
        properties.setRedirectUri("https://sp.example/aether/saml/callback");
        assertDoesNotThrow(properties::validate);
    }
}
