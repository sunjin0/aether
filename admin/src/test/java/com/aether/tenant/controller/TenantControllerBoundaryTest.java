package com.aether.tenant.controller;

import com.aether.local.CurrentUser;
import com.aether.tenant.entity.Tenant;
import com.aether.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TenantControllerBoundaryTest {
    @AfterEach
    void clearCurrentUser() { CurrentUser.remove(); }

    @Test
    void tenantUserCannotCreateTenant() {
        TenantService tenants = mock(TenantService.class);
        setTenant("tenant-a");
        Tenant request = new Tenant(); request.setCode("tenant-b"); request.setName("Tenant B");

        assertThrows(RuntimeException.class, () -> new TenantController(tenants).save(request));
        verifyNoInteractions(tenants);
    }

    @Test
    void tenantUserCannotUpdateAnotherTenant() {
        TenantService tenants = mock(TenantService.class);
        setTenant("tenant-a");
        Tenant existing = new Tenant(); existing.setId("tenant-b"); existing.setCode("tenant-b"); existing.setName("Tenant B"); existing.setDeleted(false);
        when(tenants.getById("tenant-b")).thenReturn(existing);
        Tenant request = new Tenant(); request.setId("tenant-b"); request.setCode("tenant-b-new"); request.setName("Tenant B New");

        assertThrows(RuntimeException.class, () -> new TenantController(tenants).save(request));
        verify(tenants, never()).updateById(any(Tenant.class));
    }

    @Test
    void tenantUserCannotDisableAnotherTenant() {
        TenantService tenants = mock(TenantService.class);
        setTenant("tenant-a");
        Tenant existing = new Tenant(); existing.setId("tenant-b"); existing.setDeleted(false); existing.setStatus(1);
        when(tenants.getById("tenant-b")).thenReturn(existing);

        assertThrows(RuntimeException.class, () -> new TenantController(tenants).disable("tenant-b"));
        verify(tenants, never()).updateById(any(Tenant.class));
    }

    private void setTenant(String tenantId) {
        HashMap<String, String> user = new HashMap<>(); user.put("tenantId", tenantId); CurrentUser.set(user);
    }
}
