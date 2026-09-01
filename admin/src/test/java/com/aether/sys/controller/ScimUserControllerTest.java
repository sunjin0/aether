package com.aether.sys.controller;

import com.aether.sys.service.OidcIdentityBindingService;
import com.aether.sys.service.ScimBearerTokenValidator;
import com.aether.sys.service.UserService;
import com.aether.sys.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyBoolean;

class ScimUserControllerTest {
    @Test
    void rejectsUnsupportedFilterBeforeQueryingUsers() {
        ScimBearerTokenValidator validator = mock(ScimBearerTokenValidator.class);
        UserService users = mock(UserService.class);
        when(validator.isValid("Bearer test")).thenReturn(true);
        when(validator.isTenantAllowed("tenant-a")).thenReturn(true);
        ScimUserController controller = new ScimUserController(validator, users, mock(OidcIdentityBindingService.class));

        ResponseEntity<?> response = controller.list("Bearer test", "tenant-a", 1, 100, "userName co \"admin\"");

        assertEquals(400, response.getStatusCodeValue());
        verifyNoInteractions(users);
    }

    @Test
    void createsTenantBoundUserAndMapsEmail() {
        ScimBearerTokenValidator validator = mock(ScimBearerTokenValidator.class);
        UserService users = mock(UserService.class);
        when(validator.isValid("Bearer test")).thenReturn(true);
        when(validator.isTenantAllowed("tenant-a")).thenReturn(true);
        User created = new User();
        created.setId("u-1"); created.setUsername("alice"); created.setEmail("alice@example.test"); created.setState(1);
        when(users.provisionScim("tenant-a", "alice", "alice@example.test", true)).thenReturn(created);
        ScimUserController controller = new ScimUserController(validator, users, mock(OidcIdentityBindingService.class));
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("userName", "alice");
        body.put("emails", java.util.Collections.singletonList(java.util.Collections.singletonMap("value", "alice@example.test")));

        ResponseEntity<?> response = controller.create("Bearer test", "tenant-a", body);

        assertEquals(201, response.getStatusCodeValue());
        org.mockito.Mockito.verify(users).provisionScim("tenant-a", "alice", "alice@example.test", true);
    }

    @Test
    void putRejectsIncompleteResourceBeforeLookup() {
        ScimBearerTokenValidator validator = mock(ScimBearerTokenValidator.class);
        UserService users = mock(UserService.class);
        when(validator.isValid("Bearer test")).thenReturn(true);
        when(validator.isTenantAllowed("tenant-a")).thenReturn(true);
        ScimUserController controller = new ScimUserController(validator, users, mock(OidcIdentityBindingService.class));

        ResponseEntity<?> response = controller.put("Bearer test", "tenant-a", "u-1", new java.util.HashMap<>());

        assertEquals(400, response.getStatusCodeValue());
        verifyNoInteractions(users);
    }

    @Test
    void replacesTenantBoundUserResource() {
        ScimBearerTokenValidator validator = mock(ScimBearerTokenValidator.class);
        UserService users = mock(UserService.class);
        when(validator.isValid("Bearer test")).thenReturn(true);
        when(validator.isTenantAllowed("tenant-a")).thenReturn(true);
        User updated = new User();
        updated.setId("u-1"); updated.setUsername("alice-new"); updated.setEmail("new@example.test"); updated.setState(1);
        when(users.updateScim("tenant-a", "u-1", "alice-new", "new@example.test", true)).thenReturn(updated);
        ScimUserController controller = new ScimUserController(validator, users, mock(OidcIdentityBindingService.class));
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("userName", "alice-new"); body.put("active", true);
        body.put("emails", java.util.Collections.singletonList(java.util.Collections.singletonMap("value", "new@example.test")));

        ResponseEntity<?> response = controller.put("Bearer test", "tenant-a", "u-1", body);

        assertEquals(200, response.getStatusCodeValue());
        org.mockito.Mockito.verify(users).updateScim("tenant-a", "u-1", "alice-new", "new@example.test", true);
    }
}
