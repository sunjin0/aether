package com.aether.sys.controller;

import com.aether.sys.config.OidcIdentityProperties;
import com.aether.sys.config.ScimIdentityProperties;
import com.aether.sys.config.SamlIdentityProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class IdentityControllerTest {
    @Test
    void authorizeIsNotAvailableWhenOidcIsDisabled() {
        IdentityController controller = controller(false);
        assertEquals(HttpStatus.NOT_FOUND, controller.authorize(null).getStatusCode());
    }

    @Test
    void callbackIsRejectedWhenOidcIsDisabled() {
        IdentityController controller = controller(false);
        assertEquals(404, controller.callback("code", "state", "tenant").getCode());
    }

    @Test
    void samlAuthorizeStoresTenantBoundOneTimeState() {
        OidcIdentityProperties oidc = new OidcIdentityProperties();
        SamlIdentityProperties saml = new SamlIdentityProperties();
        saml.setEnabled(true);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        IdentityController controller = new IdentityController(oidc, new ScimIdentityProperties(), saml, redis,
                null, null, null, null);

        ResponseEntity<Void> response = controller.samlAuthorize("tenant-a");

        assertEquals(302, response.getStatusCodeValue());
        String location = response.getHeaders().getFirst("Location");
        assertTrue(location != null && location.matches("/saml2/authenticate/aether\\?RelayState=[A-Za-z0-9]{32}"));
        verify(values).set(org.mockito.ArgumentMatchers.startsWith("saml:login:state:"),
                org.mockito.ArgumentMatchers.eq("tenant-a"), org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq(java.util.concurrent.TimeUnit.MINUTES));
    }

    @Test
    void samlAuthorizeRejectsInvalidTenantIdentifier() {
        SamlIdentityProperties saml = new SamlIdentityProperties();
        saml.setEnabled(true);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        IdentityController controller = new IdentityController(new OidcIdentityProperties(), new ScimIdentityProperties(),
                saml, redis, null, null, null, null);
        assertEquals(404, controller.samlAuthorize("tenant/other").getStatusCodeValue());
        verifyNoInteractions(redis);
    }

    private IdentityController controller(boolean enabled) {
        OidcIdentityProperties properties = new OidcIdentityProperties();
        properties.setEnabled(enabled);
        return new IdentityController(properties, new ScimIdentityProperties(), null, null, null, null, null);
    }
}
