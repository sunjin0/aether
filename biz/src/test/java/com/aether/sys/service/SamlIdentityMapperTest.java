package com.aether.sys.service;

import com.aether.sys.config.SamlIdentityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SamlIdentityMapperTest {
    @Test
    void rejectsWhenSamlIsDisabledOrAuthenticationIsNotSaml() {
        SamlIdentityProperties properties = new SamlIdentityProperties();
        OidcIdentityBindingService bindings = mock(OidcIdentityBindingService.class);
        SamlIdentityMapper mapper = new SamlIdentityMapper(properties, bindings);
        assertNull(mapper.findBoundIdentity("tenant-a", mock(Saml2Authentication.class)));
        assertNull(mapper.findBoundIdentity("tenant-a", mock(org.springframework.security.core.Authentication.class)));
        verifyNoInteractions(bindings);
    }
}
