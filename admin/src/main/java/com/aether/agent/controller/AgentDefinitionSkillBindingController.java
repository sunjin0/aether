package com.aether.agent.controller;

import com.aether.agent.skill.dto.AgentSkillBindingUpdateDto;
import com.aether.agent.skill.dto.AgentSkillInstallDto;
import com.aether.agent.skill.entity.AgentDefinitionSkillBinding;
import com.aether.agent.skill.entity.AgentSkill;
import com.aether.agent.skill.entity.AgentSkillVersion;
import com.aether.agent.skill.service.AgentSkillService;
import com.aether.agent.skill.vo.AgentDefinitionSkillBindingVo;
import com.aether.agent.skill.vo.AgentSkillVo;
import com.aether.entity.WebResponse;
import com.aether.i18n.I18nUtils;
import com.aether.permission.Permission;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Agent 已安装 Skill 的独立管理接口，避免与 Agent 基础配置混用。
 */
@RestController
@Api(tags = "Agent Skill 绑定 API")
@Permission(path = "/agent/definition")
@RequestMapping("/api/agent/definition")
public class AgentDefinitionSkillBindingController {
    private final AgentSkillService skillService;

    /**
     * 创建 {@code AgentDefinitionSkillBindingController} 实例。
     */
    public AgentDefinitionSkillBindingController(AgentSkillService skillService) {
        this.skillService = skillService;
    }

    /**
     * 查询当前请求。
     */
    @ApiOperation("查询 Agent 已安装 Skill")
    @GetMapping("/{agentId}/skills")
    public WebResponse<List<AgentDefinitionSkillBinding>> list(@PathVariable @NotBlank String agentId) {
        return WebResponse.OK(skillService.listBindings(agentId));
    }

    @ApiOperation("分页查询 Agent 已安装 Skill")
    @PostMapping("/{agentId}/skills/list")
    public WebResponse<List<AgentDefinitionSkillBindingVo>> listPage(@PathVariable @NotBlank String agentId,
                                                                       @RequestBody(required = false) AgentDefinitionSkillBindingVo query) {
        AgentDefinitionSkillBindingVo request = query == null ? new AgentDefinitionSkillBindingVo() : query;
        List<AgentDefinitionSkillBinding> bindings = skillService.listBindings(agentId);
        List<String> skillIds = bindings.stream().map(AgentDefinitionSkillBinding::getSkillId).distinct().collect(Collectors.toList());
        Map<String, AgentSkill> skills = skillIds.isEmpty() ? Collections.emptyMap() : skillService.listByIds(skillIds).stream()
                .collect(Collectors.toMap(AgentSkill::getId, item -> item, (left, right) -> left));
        List<AgentDefinitionSkillBinding> filtered = bindings.stream().filter(binding -> {
            if (request.getKeyword() == null || request.getKeyword().trim().isEmpty()) return true;
            AgentSkill skill = skills.get(binding.getSkillId());
            return skill != null && ((skill.getName() != null && skill.getName().contains(request.getKeyword()))
                    || (skill.getCode() != null && skill.getCode().contains(request.getKeyword())));
        }).collect(Collectors.toList());
        long total = filtered.size();
        long current = request.getCurrent() == null ? 1L : request.getCurrent();
        long pageSize = request.getPageSize() == null ? 12L : request.getPageSize();
        int from = (int) Math.min((current - 1) * pageSize, total);
        int to = (int) Math.min(from + pageSize, total);
        List<AgentDefinitionSkillBindingVo> records = filtered.subList(from, to).stream().map(binding -> {
            AgentDefinitionSkillBindingVo vo = new AgentDefinitionSkillBindingVo();
            BeanUtils.copyProperties(binding, vo);
            AgentSkill skill = skills.get(binding.getSkillId());
            if (skill != null) {
                vo.setSkillName(skill.getName());
                vo.setSkillCode(skill.getCode());
                vo.setSkillDescription(skill.getDescription());
                vo.setCategory(skill.getCategory());
                AgentSkillVersion version = skillService.listVersions(binding.getSkillId()).stream()
                        .filter(item -> binding.getSkillVersionId().equals(item.getId())).findFirst().orElse(null);
                if (version != null) vo.setVersionNo(version.getVersionNo());
            }
            return vo;
        }).collect(Collectors.toList());
        return WebResponse.Page(records, total);
    }

    @ApiOperation("查询 Agent 可安装 Skill")
    @PostMapping("/{agentId}/skills/available")
    public WebResponse<List<AgentSkillVo>> available(@PathVariable @NotBlank String agentId, @RequestBody AgentSkillVo query) {
        List<String> installedIds = skillService.listBindings(agentId).stream().map(AgentDefinitionSkillBinding::getSkillId).collect(Collectors.toList());
        Page<AgentSkill> page = skillService.page(new Page<>(query.getCurrent() == null ? 1 : query.getCurrent(), query.getPageSize() == null ? 12 : query.getPageSize()),
                Wrappers.lambdaQuery(AgentSkill.class).notIn(!installedIds.isEmpty(), AgentSkill::getId, installedIds)
                        .like(query.getName() != null && !query.getName().trim().isEmpty(), AgentSkill::getName, query.getName())
                        .like(query.getCode() != null && !query.getCode().trim().isEmpty(), AgentSkill::getCode, query.getCode())
                        .like(query.getDescription() != null && !query.getDescription().trim().isEmpty(), AgentSkill::getDescription, query.getDescription())
                        .eq(query.getCategory() != null && !query.getCategory().trim().isEmpty(), AgentSkill::getCategory, query.getCategory())
                        .eq(AgentSkill::getStatus, 1).eq(AgentSkill::getDeleted, false).orderByDesc(AgentSkill::getCreatedAt));
        return WebResponse.Page(page.getRecords().stream().map(skillService::lifecycle).collect(Collectors.toList()), page.getTotal());
    }

    /**
     * 处理install。
     */
    @ApiOperation("为 Agent 安装 Skill")
    @PostMapping("/{agentId}/skills")
    @Permission(path = "/agent/definition", type = Permission.Type.Write)
    public WebResponse<String> install(@PathVariable @NotBlank String agentId, @RequestBody AgentSkillInstallDto dto) {
        return WebResponse.OK(I18nUtils.getMessage("skill.installation.create.success"), skillService.install(agentId, dto));
    }

    /**
     * 更新当前请求。
     */
    @ApiOperation("更新 Agent Skill 绑定")
    @PutMapping("/{agentId}/skills/{bindingId}")
    @Permission(path = "/agent/definition", type = Permission.Type.Write)
    public WebResponse<Void> update(@PathVariable @NotBlank String agentId, @PathVariable @NotBlank String bindingId, @RequestBody AgentSkillBindingUpdateDto dto) {
        skillService.updateBinding(agentId, bindingId, dto);
        return WebResponse.OK(I18nUtils.getMessage("skill.installation.update.success"));
    }

    /**
     * 删除当前请求。
     */
    @ApiOperation("移除 Agent Skill 绑定")
    @DeleteMapping("/{agentId}/skills/{bindingId}")
    @Permission(path = "/agent/definition", type = Permission.Type.Write)
    public WebResponse<Void> delete(@PathVariable @NotBlank String agentId, @PathVariable @NotBlank String bindingId) {
        skillService.removeBinding(agentId, bindingId);
        return WebResponse.OK(I18nUtils.getMessage("skill.installation.delete.success"));
    }
}
