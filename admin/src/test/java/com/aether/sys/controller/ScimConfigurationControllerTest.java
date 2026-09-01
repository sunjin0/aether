package com.aether.sys.controller;

import com.aether.sys.service.ScimBearerTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScimConfigurationControllerTest {
    @Test
    void advertisesPatchProvisioning() {
        ScimBearerTokenValidator validator = mock(ScimBearerTokenValidator.class);
        when(validator.isValid("Bearer test")).thenReturn(true);
        ResponseEntity<?> response = new ScimConfigurationController(validator).serviceProviderConfig("Bearer test");
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(true, ((Map<?, ?>) body.get("patch")).get("supported"));
        assertEquals(true, ((Map<?, ?>) body.get("bulk")).get("supported"));
    }
}
