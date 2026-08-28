package com.aether.agent.controller;

import com.aether.agent.dto.AgentToolBindingDto;
import com.aether.agent.dto.AgentControllerRequests.AvailableToolList;
import com.aether.agent.dto.AgentControllerRequests.ToolBindingList;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.entity.AgentToolBinding;
import com.aether.agent.entity.AgentMcpServer;
import com.aether.agent.service.AgentToolBindingService;
import com.aether.agent.service.AgentMcpServerService;
import com.aether.agent.service.AgentToolService;
import com.aether.agent.vo.AgentToolBindingVo;
import com.aether.agent.vo.AgentToolVo;
import com.aether.entity.WebResponse;
import com.aether.i18n.I18nUtils;
import com.aether.permission.Permission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * 工具绑定管理 Controller
 */
@Api(tags = "工具绑定管理 API")
@Validated
@RestController
@Permission(path = "/agent/tool")
@RequestMapping("/api/agent/definition")
public class AgentToolBindingController {

    private final AgentToolBindingService agentToolBindingService;
    private final AgentToolService agentToolService;
    private final AgentMcpServerService agentMcpServerService;

    /**
     * 创建 {@code AgentToolBindingController} 实例。
     */
    @Autowired
    public AgentToolBindingController(AgentToolBindingService agentToolBindingService, AgentToolService agentToolService,
                                      AgentMcpServerService agentMcpServerService) {
        this.agentToolBindingService = agentToolBindingService;
        this.agentToolService = agentToolService;
        this.agentMcpServerService = agentMcpServerService;
    }

    /**
     * 查询按智能体。
     */
    @ApiOperation("查询Agent的工具绑定")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/{agentId}/tools")
    public WebResponse<List<AgentToolBindingVo>> listByAgent(@PathVariable @NotBlank String agentId) {
        return list(agentId, new AgentToolBindingVo());
    }

    @ApiOperation("分页查询Agent工具绑定")
    @PostMapping("/{agentId}/tools/list")
    public WebResponse<List<AgentToolBindingVo>> list(@PathVariable @NotBlank String agentId,
                                                        @RequestBody(required = false) ToolBindingList query) {
        ToolBindingList request = query == null ? new ToolBindingList() : query;
        long current = request.getCurrent() == null ? 1L : request.getCurrent();
        long pageSize = request.getPageSize() == null ? 12L : request.getPageSize();
        List<String> matchingToolIds = new ArrayList<>();
        if (request.getKeyword() != null && !request.getKeyword().trim().isEmpty()) {
            matchingToolIds = agentToolService.list(Wrappers.lambdaQuery(AgentTool.class)
                    .and(wrapper -> wrapper.like(AgentTool::getName, request.getKeyword())
                            .or().like(AgentTool::getCode, request.getKeyword())))
                    .stream().map(AgentTool::getId).collect(Collectors.toList());
            if (matchingToolIds.isEmpty()) {
                return WebResponse.Page(Collections.emptyList(), 0L);
            }
        }
        Page<AgentToolBinding> page = agentToolBindingService.page(new Page<>(current, pageSize),
                Wrappers.lambdaQuery(AgentToolBinding.class)
                        .eq(AgentToolBinding::getAgentDefinitionId, agentId)
                        .in(!matchingToolIds.isEmpty(), AgentToolBinding::getToolId, matchingToolIds)
                        .eq(AgentToolBinding::getDeleted, false)
                        .orderByAsc(AgentToolBinding::getPriority));
        List<String> toolIds = page.getRecords().stream().map(AgentToolBinding::getToolId).collect(Collectors.toList());
        Map<String, AgentTool> tools = toolIds.isEmpty() ? Collections.emptyMap() : agentToolService.listByIds(toolIds).stream()
                .collect(Collectors.toMap(AgentTool::getId, item -> item, (left, right) -> left));
        List<String> serverIds = page.getRecords().stream().map(item -> tools.get(item.getToolId()))
                .filter(tool -> tool != null && tool.getMcpServerId() != null).map(AgentTool::getMcpServerId)
                .distinct().collect(Collectors.toList());
        Map<String, AgentMcpServer> servers = serverIds.isEmpty() ? Collections.emptyMap() : agentMcpServerService.listByIds(serverIds).stream()
                .collect(Collectors.toMap(AgentMcpServer::getId, item -> item, (left, right) -> left));
        List<AgentToolBindingVo> vos = page.getRecords().stream().map(item -> {
            AgentToolBindingVo vo = new AgentToolBindingVo();
            AgentTool tool = tools.get(item.getToolId());
            BeanUtils.copyProperties(item, vo);
            if (tool != null) {
                vo.setToolName(tool.getName());
                vo.setToolCode(tool.getCode());
                vo.setToolDescription(tool.getDescription());
                AgentMcpServer server = servers.get(tool.getMcpServerId());
                if (server != null) {
                    vo.setMcpServerName(server.getName());
                    vo.setMcpBaseUrl(server.getBaseUrl());
                }
            }
            return vo;
        }).collect(Collectors.toList());
        return WebResponse.Page(vos, page.getTotal());
    }

    @ApiOperation("查询 Agent 可绑定工具")
    @PostMapping("/{agentId}/tools/available")
    public WebResponse<List<AgentToolVo>> available(@PathVariable @NotBlank String agentId, @RequestBody AvailableToolList query) {
        long current = query.getCurrent() == null ? 1L : query.getCurrent();
        long pageSize = query.getPageSize() == null ? 12L : query.getPageSize();
        List<String> boundIds = agentToolBindingService.list(Wrappers.lambdaQuery(AgentToolBinding.class)
                .eq(AgentToolBinding::getAgentDefinitionId, agentId).eq(AgentToolBinding::getDeleted, false))
                .stream().map(AgentToolBinding::getToolId).collect(Collectors.toList());
        Page<AgentTool> page = agentToolService.page(new Page<>(current, pageSize), Wrappers.lambdaQuery(AgentTool.class)
                .notIn(!boundIds.isEmpty(), AgentTool::getId, boundIds)
                .like(query.getName() != null && !query.getName().trim().isEmpty(), AgentTool::getName, query.getName())
                .like(query.getCode() != null && !query.getCode().trim().isEmpty(), AgentTool::getCode, query.getCode())
                .like(query.getDescription() != null && !query.getDescription().trim().isEmpty(), AgentTool::getDescription, query.getDescription())
                .eq(query.getToolType() != null && !query.getToolType().trim().isEmpty(), AgentTool::getToolType, query.getToolType())
                .eq(AgentTool::getStatus, 1).eq(AgentTool::getDeleted, false).orderByDesc(AgentTool::getCreatedAt));
        List<AgentToolVo> records = page.getRecords().stream().map(tool -> {
            AgentToolVo vo = new AgentToolVo();
            BeanUtils.copyProperties(tool, vo);
            return vo;
        }).collect(Collectors.toList());
        return WebResponse.Page(records, page.getTotal());
    }

    /**
     * 绑定工具。
     */
    @ApiOperation("绑定工具")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/definition")
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/{agentId}/tools")
    public WebResponse<Void> bind(@PathVariable @NotBlank String agentId, @RequestBody AgentToolBindingDto dto) {
        if (!agentToolBindingService.list(
                Wrappers.lambdaQuery(AgentToolBinding.class)
                        .eq(AgentToolBinding::getAgentDefinitionId, agentId)
                        .eq(AgentToolBinding::getToolId, dto.getToolId())
                        .eq(AgentToolBinding::getDeleted, false)
                        .last("limit 1")).isEmpty()) {
            return WebResponse.Error(400, I18nUtils.getMessage("bind.tool.exists"), null);
        }
        AgentToolBinding binding = new AgentToolBinding();
        binding.setAgentDefinitionId(agentId);
        binding.setToolId(dto.getToolId());
        binding.setPriority(dto.getPriority());
        binding.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        boolean saved = agentToolBindingService.save(binding);
        return WebResponse.OK(saved ? I18nUtils.getMessage("agent.tool-binding.create.success") : I18nUtils.getMessage("agent.tool-binding.create.fail"));
    }

    /**
     * 解绑工具。
     */
    @ApiOperation("解绑工具")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/definition")
    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/{agentId}/tools/{toolId}")
    public WebResponse<Void> unbind(@PathVariable @NotBlank String agentId, @PathVariable @NotBlank String toolId) {
        boolean removed = agentToolBindingService.remove(
                Wrappers.lambdaUpdate(AgentToolBinding.class)
                        .eq(AgentToolBinding::getAgentDefinitionId, agentId)
                        .eq(AgentToolBinding::getToolId, toolId));
        return WebResponse.OK(removed ? I18nUtils.getMessage("agent.tool-binding.delete.success") : I18nUtils.getMessage("agent.tool-binding.delete.fail"));
    }

    /**
     * 更新Priority。
     */
    @ApiOperation("调整优先级")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/definition")
    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/{agentId}/tools/{toolId}/priority")
    public WebResponse<Void> updatePriority(@PathVariable @NotBlank String agentId,
                                            @PathVariable @NotBlank String toolId,
                                            @RequestBody AgentToolBindingDto dto) {
        boolean updated = agentToolBindingService.update(
                Wrappers.lambdaUpdate(AgentToolBinding.class)
                        .eq(AgentToolBinding::getAgentDefinitionId, agentId)
                        .eq(AgentToolBinding::getToolId, toolId)
                        .set(AgentToolBinding::getPriority, dto.getPriority()));
        return WebResponse.OK(updated ? I18nUtils.getMessage("agent.tool-binding.priority.update.success") : I18nUtils.getMessage("agent.tool-binding.priority.update.fail"));
    }

    @ApiOperation("更新工具绑定状态")
    @Permission(path = "/agent/definition")
    @PutMapping("/{agentId}/tools/{toolId}/status")
    public WebResponse<Void> updateStatus(@PathVariable @NotBlank String agentId, @PathVariable @NotBlank String toolId,
                                          @RequestBody AgentToolBindingDto dto) {
        boolean updated = agentToolBindingService.update(Wrappers.lambdaUpdate(AgentToolBinding.class)
                .eq(AgentToolBinding::getAgentDefinitionId, agentId).eq(AgentToolBinding::getToolId, toolId)
                .set(AgentToolBinding::getStatus, dto.getStatus()));
        return WebResponse.OK(updated ? I18nUtils.getMessage("agent.tool-binding.status.update.success") : I18nUtils.getMessage("agent.tool-binding.status.update.fail"));
    }
}
