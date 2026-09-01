package com.aether.tenant.controller;

import com.aether.tenant.entity.Tenant;
import com.aether.tenant.entity.Workspace;
import com.aether.tenant.service.TenantService;
import com.aether.tenant.service.WorkspaceService;
import org.junit.jupiter.api.Test;
import com.aether.local.CurrentUser;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class WorkspaceControllerTest {
    @org.junit.jupiter.api.AfterEach
    void clearCurrentUser() { CurrentUser.remove(); }
    @Test
    void rejectsWorkspaceUnderDisabledTenant() {
        WorkspaceService workspaces = mock(WorkspaceService.class);
        TenantService tenants = mock(TenantService.class);
        Tenant tenant = new Tenant(); tenant.setId("tenant-a"); tenant.setStatus(0); tenant.setDeleted(false);
        when(tenants.getById("tenant-a")).thenReturn(tenant);
        WorkspaceController controller = new WorkspaceController(workspaces, tenants);
        Workspace request = new Workspace(); request.setTenantId("tenant-a"); request.setCode("ops"); request.setName("Ops");

        assertThrows(RuntimeException.class, () -> controller.save(request));
        verify(workspaces, never()).save(any(Workspace.class));
    }

    @Test
    void rejectsWorkspaceWriteForAnotherTenant() {
        WorkspaceService workspaces = mock(WorkspaceService.class);
        TenantService tenants = mock(TenantService.class);
        HashMap<String, String> user = new HashMap<>(); user.put("tenantId", "tenant-a"); CurrentUser.set(user);
        Workspace request = new Workspace(); request.setTenantId("tenant-b"); request.setCode("ops"); request.setName("Ops");

        assertThrows(RuntimeException.class, () -> new WorkspaceController(workspaces, tenants).save(request));
        verifyNoInteractions(tenants);
        verify(workspaces, never()).save(any(Workspace.class));
    }
}
