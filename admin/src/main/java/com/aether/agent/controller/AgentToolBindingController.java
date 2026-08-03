package com.aether.agent.controller;

import com.aether.agent.dto.AgentToolBindingDto;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.entity.AgentToolBinding;
import com.aether.agent.service.AgentToolBindingService;
import com.aether.agent.service.AgentToolService;
import com.aether.agent.vo.AgentToolBindingVo;
import com.aether.entity.WebResponse;
import com.aether.i18n.I18nUtils;
import com.aether.permission.Permission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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

    @Autowired
    public AgentToolBindingController(AgentToolBindingService agentToolBindingService, AgentToolService agentToolService) {
        this.agentToolBindingService = agentToolBindingService;
        this.agentToolService = agentToolService;
    }

    @ApiOperation("查询Agent的工具绑定")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/{agentId}/tools")
    public WebResponse<List<AgentToolBindingVo>> listByAgent(@PathVariable @NotBlank String agentId) {
        List<AgentToolBinding> list = agentToolBindingService.list(
                Wrappers.lambdaQuery(AgentToolBinding.class)
                        .eq(AgentToolBinding::getAgentDefinitionId, agentId)
                        .eq(AgentToolBinding::getDeleted, false)
                        .orderByAsc(AgentToolBinding::getPriority));
        List<AgentToolBindingVo> vos = list.stream().map(item -> {
            AgentToolBindingVo vo = new AgentToolBindingVo();
            AgentTool tool = agentToolService.getById(item.getToolId());
            BeanUtils.copyProperties(item, vo);
            if (tool != null){
                vo.setToolName(tool.getName());
                vo.setToolCode(tool.getCode());
                vo.setStatus(tool.getStatus());
            }
            return vo;
        }).collect(Collectors.toList());
        return WebResponse.OK(vos);
    }

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
}
