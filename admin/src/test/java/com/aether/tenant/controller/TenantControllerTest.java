package com.aether.tenant.controller;

import com.aether.tenant.entity.Tenant;
import com.aether.tenant.service.TenantService;
import org.junit.jupiter.api.Test;
import com.aether.entity.WebResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TenantControllerTest {
    @Test
    void rejectsDuplicateTenantCode() {
        TenantService service = mock(TenantService.class);
        Tenant duplicate = new Tenant(); duplicate.setId("tenant-1"); duplicate.setCode("acme");
        when(service.getOne(any())).thenReturn(duplicate);
        TenantController controller = new TenantController(service);
        Tenant request = new Tenant(); request.setCode("acme"); request.setName("Acme");

        WebResponse<String> response = controller.save(request);

        assertEquals(409, response.getCode());
        verify(service, never()).save(any(Tenant.class));
    }
}
