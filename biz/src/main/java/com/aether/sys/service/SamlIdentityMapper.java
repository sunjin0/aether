package com.aether.sys.service;

import com.aether.sys.config.SamlIdentityProperties;
import com.aether.sys.entity.OidcIdentityBinding;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.stereotype.Service;

/** Resolves a SAML NameID to an explicitly pre-bound local user within one tenant. */
@Service
public class SamlIdentityMapper {
    private final SamlIdentityProperties properties;
    private final OidcIdentityBindingService bindingService;

    public SamlIdentityMapper(SamlIdentityProperties properties, OidcIdentityBindingService bindingService) {
        this.properties = properties;
        this.bindingService = bindingService;
    }

    public OidcIdentityBinding findBoundIdentity(String tenantId, Authentication authentication) {
        if (!properties.isEnabled() || StringUtils.isBlank(tenantId)
                || !(authentication instanceof Saml2Authentication)) return null;
        Saml2Authentication saml = (Saml2Authentication) authentication;
        String subject = StringUtils.trimToNull(saml.getName());
        String issuer = StringUtils.trimToNull(properties.getIdpEntityId());
        if (subject == null || issuer == null) return null;
        return bindingService.find(tenantId, issuer, subject);
    }
}
