package com.aether.tenant.controller;

import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.permission.Permission;
import com.aether.local.CurrentUser;
import com.aether.tenant.entity.Project;
import com.aether.tenant.entity.Workspace;
import com.aether.tenant.service.ProjectService;
import com.aether.tenant.service.WorkspaceService;
import com.aether.agent.application.entity.AgentApplication;
import com.aether.agent.application.service.AgentApplicationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "Project 管理 API")
@RestController
@Permission(path = "/system/tenant")
@RequestMapping("/api/system/project")
public class ProjectController {
    private final ProjectService service;
    private final WorkspaceService workspaceService;
    private final AgentApplicationService applicationService;

    public ProjectController(ProjectService service, WorkspaceService workspaceService, AgentApplicationService applicationService) {
        this.service = service;
        this.workspaceService = workspaceService;
        this.applicationService = applicationService;
    }

    @ApiOperation("项目列表")
    @GetMapping
    public WebResponse<List<Project>> list(@RequestParam String workspaceId) {
        Workspace workspace = workspaceService.getById(workspaceId);
        if (workspace == null || Boolean.TRUE.equals(workspace.getDeleted()) || !Integer.valueOf(1).equals(workspace.getStatus())
                || !isCurrentTenant(workspace.getTenantId())) throw new ServerException(404, "工作空间不存在");
        return WebResponse.OK(service.list(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Project>()
                .eq("workspace_id", workspaceId).eq("deleted", false).orderByDesc("created_at")));
    }

    private boolean isCurrentTenant(String tenantId) {
        String currentTenantId = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId");
        return StringUtils.isBlank(currentTenantId) || currentTenantId.equals(tenantId);
    }

    @ApiOperation("保存项目")
    @PostMapping
    @Permission(path = "/system/tenant", type = Permission.Type.Write)
    public WebResponse<String> save(@RequestBody Project request) {
        if (request == null || StringUtils.isAnyBlank(request.getWorkspaceId(), request.getCode(), request.getName()))
            return WebResponse.Error(400, "workspaceId、code 和 name 不能为空");
        Workspace workspace = workspaceService.getById(request.getWorkspaceId());
        if (workspace == null || Boolean.TRUE.equals(workspace.getDeleted()) || !Integer.valueOf(1).equals(workspace.getStatus()))
            throw new ServerException(404, "工作空间不存在或已停用");
        if (!isCurrentTenant(workspace.getTenantId())) throw new ServerException(403, "工作空间不属于当前租户");
        if (StringUtils.isNotBlank(request.getApplicationId())) {
            AgentApplication application = applicationService.getById(request.getApplicationId());
            if (application == null || Boolean.TRUE.equals(application.getDeleted())
                    || !Integer.valueOf(1).equals(application.getStatus())
                    || (application.getTenantId() != null && !application.getTenantId().equals(workspace.getTenantId())))
                throw new ServerException(403, "应用不属于当前租户");
        }
        Project duplicate = service.getOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Project>()
                .eq("workspace_id", request.getWorkspaceId()).eq("code", request.getCode()).eq("deleted", false), false);
        if (duplicate != null && (request.getId() == null || !request.getId().equals(duplicate.getId())))
            return WebResponse.Error(409, "项目编码已存在");
        if (request.getStatus() == null) request.setStatus(1);
        if (request.getId() == null) service.save(request);
        else {
            Project existing = service.getById(request.getId());
            if (existing == null || Boolean.TRUE.equals(existing.getDeleted())
                    || !request.getWorkspaceId().equals(existing.getWorkspaceId()))
                throw new ServerException(403, "项目不属于当前工作空间");
            service.updateById(request);
        }
        return WebResponse.OK(request.getId());
    }

    @ApiOperation("停用项目")
    @PostMapping("/{id}/disable")
    @Permission(path = "/system/tenant", type = Permission.Type.Write)
    public WebResponse<Boolean> disable(@PathVariable String id) {
        Project project = service.getById(id);
        if (project == null || Boolean.TRUE.equals(project.getDeleted())) throw new ServerException(404, "项目不存在");
        project.setStatus(0);
        return WebResponse.OK(service.updateById(project));
    }
}
