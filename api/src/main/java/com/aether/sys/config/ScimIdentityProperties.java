package com.aether.sys.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** SCIM provisioning contract; disabled until an isolated management credential is configured. */
@Data
@Component
@ConfigurationProperties(prefix = "aether.identity.scim")
public class ScimIdentityProperties {
    private boolean enabled = false;
    private String basePath = "/scim/v2";
    private String bearerToken;
    private String tenantId;

    @javax.annotation.PostConstruct
    public void validate() {
        if (!enabled) return;
        if (!StringUtils.hasText(basePath) || !basePath.startsWith("/") || !StringUtils.hasText(bearerToken)
                || !StringUtils.hasText(tenantId))
            throw new IllegalStateException("SCIM enabled requires an absolute base-path, bearer-token and tenant-id");
    }
}
