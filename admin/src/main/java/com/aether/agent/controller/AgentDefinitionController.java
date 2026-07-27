package com.aether.agent.controller;

import com.aether.agent.dto.AgentDefinitionDto;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentToolBinding;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.AgentToolBindingService;
import com.aether.agent.service.ModelProviderService;
import com.aether.agent.vo.AgentDefinitionVo;
import com.aether.agent.vo.AgentToolBindingVo;
import com.aether.entity.Option;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.permission.Permission;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent定义管理 Controller
 */
@Api(tags = "Agent定义管理 API")
@Validated
@RestController
@Permission(path = "/agent/definition")
@RequestMapping("/api/agent/definition")
public class AgentDefinitionController {

    private final AgentDefinitionService agentDefinitionService;
    private final AgentToolBindingService agentToolBindingService;
    private final ModelProviderService modelProviderService;

    @Autowired
    public AgentDefinitionController(AgentDefinitionService agentDefinitionService,
                                     AgentToolBindingService agentToolBindingService,
                                     ModelProviderService modelProviderService) {
        this.agentDefinitionService = agentDefinitionService;
        this.agentToolBindingService = agentToolBindingService;
        this.modelProviderService = modelProviderService;
    }

    @ApiOperation("Agent定义列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping("/list")
    public WebResponse<List<AgentDefinitionVo>> list(@RequestBody AgentDefinitionVo vo) {
        Page<AgentDefinition> page = new Page<>(vo.getCurrent(), vo.getPageSize());
        Wrapper<AgentDefinition> wrapper = Wrappers.lambdaQuery(AgentDefinition.class)
                .like(StringUtils.isNotBlank(vo.getName()), AgentDefinition::getName, vo.getName())
                .like(StringUtils.isNotBlank(vo.getCode()), AgentDefinition::getCode, vo.getCode())
                .eq(vo.getStatus() != null, AgentDefinition::getStatus, vo.getStatus())
                .eq(StringUtils.isNotBlank(vo.getModelProviderId()), AgentDefinition::getModelProviderId, vo.getModelProviderId())
                .eq(AgentDefinition::getDeleted, false)
                .orderByDesc(AgentDefinition::getCreatedAt);
        Page<AgentDefinition> result = agentDefinitionService.page(page, wrapper);
        List<AgentDefinitionVo> list = result.getRecords().stream().map(item -> {
            AgentDefinitionVo itemVo = new AgentDefinitionVo();
            BeanUtils.copyProperties(item, itemVo);
            return itemVo;
        }).collect(Collectors.toList());
        return WebResponse.Page(list, result.getTotal());
    }

    @ApiOperation("Agent下拉选项")
    @Permission(required = false)
    @GetMapping("/options")
    public WebResponse<List<Option>> options(@RequestParam(value = "status", required = false, defaultValue = "1") Integer status) {
        List<Option> options = agentDefinitionService.list(Wrappers.lambdaQuery(AgentDefinition.class)
                        .eq(status != null, AgentDefinition::getStatus, status)
                        .eq(AgentDefinition::getDeleted, false)
                        .orderByAsc(AgentDefinition::getName))
                .stream().map(item -> new Option(item.getName(), item.getId())).collect(Collectors.toList());
        return WebResponse.OK(options);
    }

    @ApiOperation("Agent定义详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "Agent定义ID", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/{id}")
    public WebResponse<AgentDefinitionVo> detail(@PathVariable @NotBlank String id) {
        AgentDefinition definition = agentDefinitionService.getById(id);
        if (definition == null || Boolean.TRUE.equals(definition.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("resource.not.found"));
        }
        AgentDefinitionVo vo = new AgentDefinitionVo();
        List<String> toolIds = agentToolBindingService.lambdaQuery()
                .eq(AgentToolBinding::getAgentDefinitionId, id)
                .eq(AgentToolBinding::getStatus, 1)
                .list()
                .stream().map(AgentToolBinding::getToolId).collect(Collectors.toList());
        vo.setToolIds(toolIds);
        BeanUtils.copyProperties(definition, vo);
        return WebResponse.OK(vo);
    }

    @ApiOperation("新增Agent定义")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/definition", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    @PostMapping
    public WebResponse<String> save(@RequestBody AgentDefinitionDto dto) {
        AgentDefinition definition = new AgentDefinition();
        BeanUtils.copyProperties(dto, definition);
        boolean saved = agentDefinitionService.save(definition);
        // 绑定工具
        if (dto.getToolIds() != null && !dto.getToolIds().isEmpty()) {
            for (String toolId : dto.getToolIds()) {
                AgentToolBinding binding = new AgentToolBinding();
                binding.setAgentDefinitionId(definition.getId());
                binding.setToolId(toolId);
                binding.setStatus(1);
                agentToolBindingService.save(binding);
            }
        }
        return WebResponse.OK(saved ? I18nUtils.getMessage("add.success") : I18nUtils.getMessage("add.fail"), definition.getId());
    }

    @ApiOperation("编辑Agent定义")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/definition", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/{id}")
    public WebResponse<Void> update(@PathVariable @NotBlank String id, @RequestBody AgentDefinitionDto dto) {
        AgentDefinition definition = new AgentDefinition();
        BeanUtils.copyProperties(dto, definition);
        definition.setId(id);
        boolean updated = agentDefinitionService.updateById(definition);
        // 重新绑定工具
        if (dto.getToolIds() != null) {
            agentToolBindingService.remove(Wrappers.lambdaUpdate(AgentToolBinding.class)
                    .eq(AgentToolBinding::getAgentDefinitionId, id));
            for (String toolId : dto.getToolIds()) {
                AgentToolBinding binding = new AgentToolBinding();
                binding.setAgentDefinitionId(id);
                binding.setToolId(toolId);
                binding.setStatus(1);
                agentToolBindingService.save(binding);
            }
        }
        return WebResponse.OK(updated ? I18nUtils.getMessage("update.success") : I18nUtils.getMessage("update.fail"));
    }

    @ApiOperation("删除Agent定义")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "Agent定义ID", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/definition", type = Permission.Type.Write)
    @DeleteMapping("/{id}")
    public WebResponse<Void> delete(@PathVariable @NotBlank String id) {
        boolean removed = agentDefinitionService.removeById(id);
        return WebResponse.OK(removed ? I18nUtils.getMessage("delete.success") : I18nUtils.getMessage("delete.fail"));
    }

    @ApiOperation("启用/禁用Agent定义")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/definition", type = Permission.Type.Write)
    @PutMapping("/{id}/status")
    public WebResponse<Void> updateStatus(@PathVariable @NotBlank String id, @RequestBody AgentDefinitionVo vo) {
        AgentDefinition definition = new AgentDefinition();
        definition.setId(id);
        definition.setStatus(vo.getStatus());
        boolean updated = agentDefinitionService.updateById(definition);
        return WebResponse.OK(updated ? I18nUtils.getMessage("update.success") : I18nUtils.getMessage("update.fail"));
    }

    @ApiOperation("复制Agent定义")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/definition", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/{id}/copy")
    public WebResponse<String> copy(@PathVariable @NotBlank String id) {
        AgentDefinition source = agentDefinitionService.getById(id);
        if (source == null || Boolean.TRUE.equals(source.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("resource.not.found"));
        }
        AgentDefinition copy = new AgentDefinition();
        BeanUtils.copyProperties(source, copy);
        copy.setId(null);
        copy.setCode(source.getCode() + "_copy");
        copy.setName(source.getName() + " (副本)");
        copy.setStatus(0); // 草稿状态
        agentDefinitionService.save(copy);
        // 复制工具绑定
        List<AgentToolBinding> bindings = agentToolBindingService.list(
                Wrappers.lambdaQuery(AgentToolBinding.class)
                        .eq(AgentToolBinding::getAgentDefinitionId, id)
                        .eq(AgentToolBinding::getDeleted, false));
        for (AgentToolBinding binding : bindings) {
            AgentToolBinding newBinding = new AgentToolBinding();
            BeanUtils.copyProperties(binding, newBinding);
            newBinding.setId(null);
            newBinding.setAgentDefinitionId(copy.getId());
            agentToolBindingService.save(newBinding);
        }
        return WebResponse.OK(I18nUtils.getMessage("copy.success"), copy.getId());
    }
    /**
     * 模型供应商列表
     * @return 模型供应商列表
     */
    @GetMapping("/model/providers")
    public WebResponse<List<Option>> getModelProviders() {
        return WebResponse.OK(modelProviderService.getModelProviders());
    }
}
