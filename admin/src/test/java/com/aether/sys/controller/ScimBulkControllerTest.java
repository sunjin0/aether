package com.aether.sys.controller;

import com.aether.sys.entity.User;
import com.aether.sys.service.OidcIdentityBindingService;
import com.aether.sys.service.RoleService;
import com.aether.sys.service.ScimBearerTokenValidator;
import com.aether.sys.service.UserRoleService;
import com.aether.sys.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ScimBulkControllerTest {
    @Test
    void rejectsOversizedBulkBeforeDispatching() {
        ScimUserController users = mock(ScimUserController.class);
        ScimGroupController groups = mock(ScimGroupController.class);
        ScimBulkController controller = new ScimBulkController(users, groups);
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("Operations", Collections.nCopies(101, Collections.singletonMap("method", "POST")));

        ResponseEntity<?> response = controller.bulk("Bearer test", "tenant-a", body);

        assertEquals(400, response.getStatusCodeValue());
        verifyNoInteractions(users, groups);
    }

    @Test
    void dispatchesTenantScopedUserCreateAndReturnsBulkStatus() {
        ScimUserController users = mock(ScimUserController.class);
        ScimGroupController groups = mock(ScimGroupController.class);
        ScimBulkController controller = new ScimBulkController(users, groups);
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("userName", "alice");
        User user = new User(); user.setId("u-1"); user.setUsername("alice"); user.setState(1);
        ResponseEntity<?> created = ResponseEntity.status(201).body(user);
        doReturn(created).when(users).create("Bearer test", "tenant-a", data);
        Map<String, Object> operation = new HashMap<String, Object>();
        operation.put("method", "POST"); operation.put("path", "/Users"); operation.put("data", data);
        Map<String, Object> body = Collections.<String, Object>singletonMap("Operations", Collections.singletonList(operation));

        ResponseEntity<?> response = controller.bulk("Bearer test", "tenant-a", body);

        assertEquals(200, response.getStatusCodeValue());
        verify(users).create("Bearer test", "tenant-a", data);
    }

    @Test
    void rejectsPathTraversalWithoutDispatching() {
        ScimBulkController controller = new ScimBulkController(mock(ScimUserController.class), mock(ScimGroupController.class));
        Map<String, Object> operation = new HashMap<String, Object>();
        operation.put("method", "DELETE"); operation.put("path", "/Users/../secrets");
        Map<String, Object> body = Collections.<String, Object>singletonMap("Operations", Collections.singletonList(operation));

        ResponseEntity<?> response = controller.bulk("Bearer test", "tenant-a", body);

        assertEquals(200, response.getStatusCodeValue());
        Map<?, ?> result = (Map<?, ?>) ((List<?>) ((Map<?, ?>) response.getBody()).get("Operations")).get(0);
        assertEquals("400", result.get("status"));
    }

    @Test
    void stopsAfterConfiguredErrorCount() {
        ScimUserController users = mock(ScimUserController.class);
        ScimGroupController groups = mock(ScimGroupController.class);
        ScimBulkController controller = new ScimBulkController(users, groups);
        Map<String, Object> bad = new HashMap<String, Object>();
        bad.put("method", "DELETE"); bad.put("path", "/Users/../secrets");
        Map<String, Object> later = new HashMap<String, Object>();
        later.put("method", "POST"); later.put("path", "/Users"); later.put("data", Collections.singletonMap("userName", "alice"));
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("failOnErrors", 1); body.put("Operations", Arrays.asList(bad, later));

        ResponseEntity<?> response = controller.bulk("Bearer test", "tenant-a", body);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(1, ((List<?>) ((Map<?, ?>) response.getBody()).get("Operations")).size());
        verifyNoInteractions(users, groups);
    }
}
