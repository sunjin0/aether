package com.aether.sys.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** SAML federation contract; disabled until a dedicated SAML adapter is enabled. */
@Data
@Component
@ConfigurationProperties(prefix = "aether.identity.saml")
public class SamlIdentityProperties {
    private boolean enabled = false;
    /** Use the IdP metadata document instead of manually supplied IdP endpoints. */
    private boolean metadataDriven = false;
    private String entityId;
    private String metadataUri;
    private String idpEntityId;
    /** IdP Single Sign-On endpoint used by the future protocol adapter. */
    private String ssoUrl;
    private String certificate;
    private String redirectUri;

    @javax.annotation.PostConstruct
    public void validate() {
        if (!enabled) return;
        if (!StringUtils.hasText(entityId) || !StringUtils.hasText(metadataUri) || !StringUtils.hasText(redirectUri)
                || (!metadataDriven && (!StringUtils.hasText(idpEntityId) || !StringUtils.hasText(certificate) || !StringUtils.hasText(ssoUrl)))) {
            throw new IllegalStateException("SAML enabled requires entity-id, metadata-uri and redirect-uri; manual mode additionally requires idp-entity-id, certificate and sso-url");
        }
        if (!metadataUri.startsWith("https://") || !redirectUri.startsWith("https://")
                || (!metadataDriven && !ssoUrl.startsWith("https://")))
            throw new IllegalStateException("SAML metadata-uri, sso-url and redirect-uri must use HTTPS");
    }
}
