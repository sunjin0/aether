package com.aether.sys.controller;

import com.aether.sys.service.RoleService;
import com.aether.sys.service.ScimBearerTokenValidator;
import com.aether.sys.service.UserRoleService;
import com.aether.sys.entity.Role;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class ScimGroupControllerTest {
    @Test
    void rejectsUnsupportedFilterBeforeQueryingGroups() {
        ScimBearerTokenValidator validator = mock(ScimBearerTokenValidator.class);
        RoleService roles = mock(RoleService.class);
        when(validator.isValid("Bearer test")).thenReturn(true);
        when(validator.isTenantAllowed("tenant-a")).thenReturn(true);
        ScimGroupController controller = new ScimGroupController(validator, roles, mock(UserRoleService.class));

        ResponseEntity<?> response = controller.list("Bearer test", "tenant-a", 1, 100, "displayName co \"ops\"");

        assertEquals(400, response.getStatusCodeValue());
        verifyNoInteractions(roles);
    }

    @Test
    void exposesActiveStateInGroupResource() {
        ScimBearerTokenValidator validator = mock(ScimBearerTokenValidator.class);
        RoleService roles = mock(RoleService.class);
        UserRoleService relations = mock(UserRoleService.class);
        when(validator.isValid("Bearer test")).thenReturn(true);
        when(validator.isTenantAllowed("tenant-a")).thenReturn(true);
        Role role = new Role();
        role.setTenantId("tenant-a");
        role.setName("on-call");
        role.setState(0);
        Page<Role> page = new Page<Role>(1, 100, 1);
        page.setRecords(java.util.Collections.singletonList(role));
        when(roles.page(org.mockito.ArgumentMatchers.any(Page.class), org.mockito.ArgumentMatchers.any())).thenReturn(page);
        when(relations.list(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Collections.emptyList());
        ScimGroupController controller = new ScimGroupController(validator, roles, relations);

        ResponseEntity<?> response = controller.list("Bearer test", "tenant-a", 1, 100, null);

        java.util.Map<?, ?> body = (java.util.Map<?, ?>) response.getBody();
        java.util.List<?> resources = (java.util.List<?>) body.get("Resources");
        assertEquals(false, ((java.util.Map<?, ?>) resources.get(0)).get("active"));
        verify(relations).list(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void putRejectsIncompleteResourceBeforeLookup() {
        ScimBearerTokenValidator validator = mock(ScimBearerTokenValidator.class);
        RoleService roles = mock(RoleService.class);
        when(validator.isValid("Bearer test")).thenReturn(true);
        when(validator.isTenantAllowed("tenant-a")).thenReturn(true);
        ScimGroupController controller = new ScimGroupController(validator, roles, mock(UserRoleService.class));

        ResponseEntity<?> response = controller.put("Bearer test", "tenant-a", "role-1", new java.util.LinkedHashMap<>());

        assertEquals(400, response.getStatusCodeValue());
        verifyNoInteractions(roles);
    }

    @Test
    void rejectsDuplicateNameDuringPut() {
        ScimBearerTokenValidator validator = mock(ScimBearerTokenValidator.class);
        RoleService roles = mock(RoleService.class);
        when(validator.isValid("Bearer test")).thenReturn(true);
        when(validator.isTenantAllowed("tenant-a")).thenReturn(true);
        Role current = new Role(); current.setId("role-1"); current.setTenantId("tenant-a"); current.setName("old");
        Role duplicate = new Role(); duplicate.setId("role-2"); duplicate.setTenantId("tenant-a"); duplicate.setName("ops");
        when(roles.getOne(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(false)))
                .thenReturn(current, duplicate);
        ScimGroupController controller = new ScimGroupController(validator, roles, mock(UserRoleService.class));
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>(); body.put("displayName", "ops");

        ResponseEntity<?> response = controller.put("Bearer test", "tenant-a", "role-1", body);

        assertEquals(409, response.getStatusCodeValue());
        org.mockito.Mockito.verify(roles, org.mockito.Mockito.never()).updateById(org.mockito.ArgumentMatchers.any());
    }
}
