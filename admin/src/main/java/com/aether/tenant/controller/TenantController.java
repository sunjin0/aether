package com.aether.tenant.controller;

import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.permission.Permission;
import com.aether.local.CurrentUser;
import com.aether.tenant.entity.Tenant;
import com.aether.tenant.service.TenantService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "Tenant 管理 API")
@RestController
@Permission(path = "/system/tenant")
@RequestMapping("/api/system/tenant")
public class TenantController {
    private final TenantService service;

    public TenantController(TenantService service) { this.service = service; }

    @ApiOperation("租户列表")
    @GetMapping
    public WebResponse<List<Tenant>> list() {
        return WebResponse.OK(service.list(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Tenant>()
                .eq("deleted", false)
                .eq(StringUtils.isNotBlank(currentTenantId()), "id", currentTenantId())
                .orderByDesc("created_at")));
    }

    private String currentTenantId() {
        return CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId");
    }

    @ApiOperation("保存租户")
    @PostMapping
    @Permission(path = "/system/tenant", type = Permission.Type.Write)
    public WebResponse<String> save(@RequestBody Tenant request) {
        if (request == null || StringUtils.isAnyBlank(request.getCode(), request.getName()))
            return WebResponse.Error(400, "code 和 name 不能为空");
        if (request.getId() == null && StringUtils.isNotBlank(currentTenantId()))
            throw new ServerException(403, "当前租户无权创建租户");
        Tenant duplicate = service.getOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Tenant>()
                .eq("code", request.getCode()).eq("deleted", false));
        if (duplicate != null && (request.getId() == null || !request.getId().equals(duplicate.getId())))
            return WebResponse.Error(409, "租户编码已存在");
        if (request.getStatus() == null) request.setStatus(1);
        if (request.getId() == null) service.save(request);
        else {
            Tenant existing = service.getById(request.getId());
            if (existing == null || Boolean.TRUE.equals(existing.getDeleted())) throw new ServerException(404, "租户不存在");
            requireCurrentTenant(existing.getId());
            request.setId(existing.getId());
            service.updateById(request);
        }
        return WebResponse.OK(request.getId());
    }

    @ApiOperation("停用租户")
    @PostMapping("/{id}/disable")
    @Permission(path = "/system/tenant", type = Permission.Type.Write)
    public WebResponse<Boolean> disable(@PathVariable String id) {
        Tenant tenant = service.getById(id);
        if (tenant == null || Boolean.TRUE.equals(tenant.getDeleted())) throw new ServerException(404, "租户不存在");
        requireCurrentTenant(tenant.getId());
        tenant.setStatus(0);
        return WebResponse.OK(service.updateById(tenant));
    }

    private void requireCurrentTenant(String tenantId) {
        String currentTenantId = currentTenantId();
        if (StringUtils.isNotBlank(currentTenantId) && !currentTenantId.equals(tenantId))
            throw new ServerException(404, "租户不存在");
    }
}
