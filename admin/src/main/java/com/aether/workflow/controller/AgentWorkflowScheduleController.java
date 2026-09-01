package com.aether.workflow.controller;

import com.aether.entity.WebResponse;
import com.aether.i18n.I18nUtils;
import com.aether.permission.Permission;
import com.aether.local.CurrentUser;
import com.aether.workflow.dto.AgentWorkflowCreateScheduleRequest;
import com.aether.workflow.dto.AgentWorkflowListSchedulesRequest;
import com.aether.workflow.dto.AgentWorkflowScheduleTriggerDto;
import com.aether.workflow.dto.AgentWorkflowUpdateScheduleRequest;
import com.aether.workflow.entity.AgentWorkflowScheduleTrigger;
import com.aether.workflow.service.AgentWorkflowScheduleTriggerService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.BeanUtils;

import java.util.List;

/**
 * 工作流定时任务的独立管理 API。
 */
@Api(tags = "工作流定时任务 API")
@RestController
@Permission(path = "/workflow/schedule")
@RequestMapping("/api/agent/workflow/schedules")
public class AgentWorkflowScheduleController {
    private final AgentWorkflowScheduleTriggerService scheduleTriggerService;

    /**
     * 创建 {@code AgentWorkflowScheduleController} 实例。
     */
    public AgentWorkflowScheduleController(AgentWorkflowScheduleTriggerService scheduleTriggerService) {
        this.scheduleTriggerService = scheduleTriggerService;
    }

    /**
     * 创建当前请求。
     */
    @ApiOperation("创建工作流定时任务")
    @Permission(path = "/workflow/schedule", type = Permission.Type.Write)
    @PostMapping
    public WebResponse<AgentWorkflowScheduleTrigger> create(@RequestBody AgentWorkflowCreateScheduleRequest request) {
        AgentWorkflowScheduleTriggerDto dto = toScheduleDto(request);
        return WebResponse.OK(I18nUtils.getMessage("workflow.schedule.create.success"), scheduleTriggerService.create(dto));
    }

    /**
     * 工作流定时任务列表。
     */
    @ApiOperation("工作流定时任务列表")

    @Permission(path = "/workflow/schedule")
    @PostMapping("/list")
    public WebResponse<List<AgentWorkflowScheduleTrigger>> list(@RequestBody(required = false) AgentWorkflowListSchedulesRequest query) {
        AgentWorkflowListSchedulesRequest condition = query == null ? new AgentWorkflowListSchedulesRequest() : query;
        long current = condition.getCurrent() == null ? 1L : condition.getCurrent();
        long pageSize = condition.getPageSize() == null ? 10L : condition.getPageSize();
        Page<AgentWorkflowScheduleTrigger> page = scheduleTriggerService.page(new Page<AgentWorkflowScheduleTrigger>(current, pageSize), Wrappers.lambdaQuery(AgentWorkflowScheduleTrigger.class)
                .like(StringUtils.isNotBlank(condition.getName()), AgentWorkflowScheduleTrigger::getName, condition.getName())
                .eq(StringUtils.isNotBlank(condition.getWorkflowId()), AgentWorkflowScheduleTrigger::getWorkflowId, condition.getWorkflowId())
                .eq(StringUtils.isNotBlank(currentTenantId()), AgentWorkflowScheduleTrigger::getTenantId, currentTenantId())
                .eq(condition.getEnabled() != null, AgentWorkflowScheduleTrigger::getEnabled, condition.getEnabled())
                .eq(AgentWorkflowScheduleTrigger::getDeleted, false)
                .orderByDesc(AgentWorkflowScheduleTrigger::getCreatedAt));
        return WebResponse.Page(page.getRecords(), page.getTotal());
    }

    private String currentTenantId() {
        return CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId");
    }

    /**
     * 更新当前请求。
     */
    @ApiOperation("编辑工作流定时任务")
    @Permission(path = "/workflow/schedule", type = Permission.Type.Write)
    @PutMapping("/{id}")
    public WebResponse<Void> update(@PathVariable String id, @RequestBody AgentWorkflowUpdateScheduleRequest request) {
        AgentWorkflowScheduleTriggerDto dto = toScheduleDto(request);
        scheduleTriggerService.update(id, dto);
        return WebResponse.OK(I18nUtils.getMessage("workflow.schedule.status.update.success"));
    }

    /**
     * 启用或停用工作流定时任务。
     */
    @ApiOperation("启用或停用工作流定时任务")
    @Permission(path = "/workflow/schedule", type = Permission.Type.Write)
    @PostMapping("/{id}/enabled")
    public WebResponse<Void> setEnabled(@PathVariable String id, @RequestParam boolean enabled) {
        scheduleTriggerService.setEnabled(id, enabled);
        return WebResponse.OK(I18nUtils.getMessage("workflow.schedule.status.update.success"));
    }

    /**
     * 删除当前请求。
     */
    @ApiOperation("删除工作流定时任务")
    @Permission(path = "/workflow/schedule", type = Permission.Type.Write)
    @DeleteMapping("/{id}")
    public WebResponse<Void> delete(@PathVariable String id) {
        scheduleTriggerService.delete(id);
        return WebResponse.OK(I18nUtils.getMessage("workflow.schedule.status.update.success"));
    }

    private AgentWorkflowScheduleTriggerDto toScheduleDto(Object request) {
        if (request == null) return null;
        AgentWorkflowScheduleTriggerDto dto = new AgentWorkflowScheduleTriggerDto();
        BeanUtils.copyProperties(request, dto);
        return dto;
    }
}
