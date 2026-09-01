package com.aether.sys.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * OIDC 企业身份配置。默认关闭，Client Secret 仅由运行环境注入。
 */
@Data
@Component
@ConfigurationProperties(prefix = "aether.identity.oidc")
public class OidcIdentityProperties {
    private boolean enabled = false;
    private String issuerUri;
    private String authorizationUri;
    private String tokenUri;
    private String jwksUri;
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private boolean autoProvision = false;
    private List<String> scopes = new ArrayList<String>();

    @javax.annotation.PostConstruct
    public void validate() {
        if (!enabled) return;
        if (!StringUtils.hasText(issuerUri) || !StringUtils.hasText(authorizationUri) || !StringUtils.hasText(clientId)
                || !StringUtils.hasText(clientSecret) || !StringUtils.hasText(tokenUri) || !StringUtils.hasText(jwksUri)
                || !StringUtils.hasText(redirectUri)
                || scopes == null || scopes.isEmpty()) {
            throw new IllegalStateException("OIDC enabled requires issuer-uri, client-id, client-secret, token-uri, jwks-uri, redirect-uri and scopes");
        }
    }
}
