package com.aether.agent.controller;

import com.aether.agent.skill.dto.AgentSkillBindingUpdateDto;
import com.aether.agent.skill.dto.AgentSkillInstallDto;
import com.aether.agent.skill.entity.AgentDefinitionSkillBinding;
import com.aether.agent.skill.service.AgentSkillService;
import com.aether.entity.WebResponse;
import com.aether.permission.Permission;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.List;

/** Agent 已安装 Skill 的独立管理接口，避免与 Agent 基础配置混用。 */
@RestController
@Permission(path = "/agent/definition")
@RequestMapping("/api/agent/definition")
public class AgentDefinitionSkillBindingController {
    private final AgentSkillService skillService;
    public AgentDefinitionSkillBindingController(AgentSkillService skillService) { this.skillService = skillService; }

    @GetMapping("/{agentId}/skills")
    public WebResponse<List<AgentDefinitionSkillBinding>> list(@PathVariable @NotBlank String agentId) { return WebResponse.OK(skillService.listBindings(agentId)); }
    @PostMapping("/{agentId}/skills")
    @Permission(path = "/agent/definition", type = Permission.Type.Write)
    public WebResponse<String> install(@PathVariable @NotBlank String agentId, @RequestBody AgentSkillInstallDto dto) { return WebResponse.OK("Skill installed", skillService.install(agentId, dto)); }
    @PutMapping("/{agentId}/skills/{bindingId}")
    @Permission(path = "/agent/definition", type = Permission.Type.Write)
    public WebResponse<Void> update(@PathVariable @NotBlank String agentId, @PathVariable @NotBlank String bindingId, @RequestBody AgentSkillBindingUpdateDto dto) { skillService.updateBinding(agentId, bindingId, dto); return WebResponse.OK("Skill installation updated"); }
    @DeleteMapping("/{agentId}/skills/{bindingId}")
    @Permission(path = "/agent/definition", type = Permission.Type.Write)
    public WebResponse<Void> delete(@PathVariable @NotBlank String agentId, @PathVariable @NotBlank String bindingId) { skillService.removeBinding(agentId, bindingId); return WebResponse.OK("Skill uninstalled"); }
}
