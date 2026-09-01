package com.aether.tenant.controller;

import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.permission.Permission;
import com.aether.local.CurrentUser;
import com.aether.tenant.entity.Workspace;
import com.aether.tenant.service.WorkspaceService;
import com.aether.tenant.service.TenantService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "Workspace 管理 API")
@RestController
@Permission(path = "/sys/workspace")
@RequestMapping("/api/system/workspace")
public class WorkspaceController {
    private final WorkspaceService service;
    private final TenantService tenantService;

    public WorkspaceController(WorkspaceService service, TenantService tenantService) {
        this.service = service;
        this.tenantService = tenantService;
    }

    @ApiOperation("工作空间列表")
    @GetMapping
    public WebResponse<List<Workspace>> list(@RequestParam String tenantId) {
        requireCurrentTenant(tenantId);
        return WebResponse.OK(service.list(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Workspace>()
                .eq("tenant_id", tenantId).eq("deleted", false).orderByDesc("created_at")));
    }

    private void requireCurrentTenant(String tenantId) {
        String currentTenantId = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId");
        if (StringUtils.isNotBlank(currentTenantId) && !currentTenantId.equals(tenantId))
            throw new ServerException(404, "租户不存在");
    }

    @ApiOperation("保存工作空间")
    @PostMapping
    @Permission(path = "/sys/workspace", type = Permission.Type.Write)
    public WebResponse<String> save(@RequestBody Workspace request) {
        if (request == null || StringUtils.isAnyBlank(request.getTenantId(), request.getCode(), request.getName()))
            return WebResponse.Error(400, "tenantId、code 和 name 不能为空");
        requireCurrentTenant(request.getTenantId());
        com.aether.tenant.entity.Tenant tenant = tenantService.getById(request.getTenantId());
        if (tenant == null || Boolean.TRUE.equals(tenant.getDeleted()) || !Integer.valueOf(1).equals(tenant.getStatus()))
            throw new ServerException(404, "租户不存在或已停用");
        Workspace duplicate = service.getOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Workspace>()
                .eq("tenant_id", request.getTenantId()).eq("code", request.getCode()).eq("deleted", false), false);
        if (duplicate != null && (request.getId() == null || !request.getId().equals(duplicate.getId())))
            return WebResponse.Error(409, "工作空间编码已存在");
        if (request.getStatus() == null) request.setStatus(1);
        if (request.getId() == null) service.save(request);
        else {
            Workspace existing = service.getById(request.getId());
            if (existing == null || Boolean.TRUE.equals(existing.getDeleted())
                    || !request.getTenantId().equals(existing.getTenantId()))
                throw new ServerException(403, "工作空间不属于当前租户");
            service.updateById(request);
        }
        return WebResponse.OK(request.getId());
    }

    @ApiOperation("停用工作空间")
    @PostMapping("/{id}/disable")
    @Permission(path = "/sys/workspace", type = Permission.Type.Write)
    public WebResponse<Boolean> disable(@PathVariable String id) {
        Workspace workspace = service.getById(id);
        if (workspace == null || Boolean.TRUE.equals(workspace.getDeleted())) throw new ServerException(404, "工作空间不存在");
        workspace.setStatus(0);
        return WebResponse.OK(service.updateById(workspace));
    }
}
