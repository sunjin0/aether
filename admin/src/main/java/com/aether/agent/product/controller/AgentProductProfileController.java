package com.aether.agent.product.controller;

import com.aether.agent.application.service.AgentApplicationService;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.product.dto.AgentProductProfileDto;
import com.aether.agent.product.entity.AgentProductProfile;
import com.aether.agent.product.service.AgentProductProfileService;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.permission.Permission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;
import java.util.List;

/** 智能客服、智能问答与业务助手的可发布产品配置。 */
@RestController
@RequestMapping("/api/agent/product-profile")
@Permission(path = "/agent/definition")
public class AgentProductProfileController {
    private final AgentProductProfileService profileService;
    private final AgentDefinitionService agentService;
    private final AgentApplicationService applicationService;
    public AgentProductProfileController(AgentProductProfileService profileService, AgentDefinitionService agentService, AgentApplicationService applicationService) {
        this.profileService = profileService; this.agentService = agentService; this.applicationService = applicationService;
    }
    @GetMapping
    public WebResponse<List<AgentProductProfile>> list(@RequestParam(required = false) String applicationId) {
        return WebResponse.OK(profileService.list(Wrappers.lambdaQuery(AgentProductProfile.class)
                .eq(StringUtils.isNotBlank(applicationId), AgentProductProfile::getApplicationId, applicationId)
                .eq(AgentProductProfile::getDeleted, false).orderByDesc(AgentProductProfile::getUpdatedAt)));
    }
    @PostMapping
    @Permission(path = "/agent/definition", type = Permission.Type.Write)
    public WebResponse<String> create(@RequestBody AgentProductProfileDto dto) {
        AgentProductProfile value = new AgentProductProfile(); BeanUtils.copyProperties(dto, value); validate(value);
        value.setStatus(0); value.setVersionNo(0); profileService.save(value); return WebResponse.OK("创建成功", value.getId());
    }
    @PutMapping("/{id}")
    @Permission(path = "/agent/definition", type = Permission.Type.Write)
    public WebResponse<Void> update(@PathVariable String id, @RequestBody AgentProductProfileDto dto) {
        AgentProductProfile value = required(id); BeanUtils.copyProperties(dto, value); validate(value); profileService.updateById(value); return WebResponse.OK("更新成功");
    }
    @PostMapping("/{id}/publish")
    @Permission(path = "/agent/definition", type = Permission.Type.Write)
    public WebResponse<AgentProductProfile> publish(@PathVariable String id) {
        AgentProductProfile value = required(id); validate(value); value.setStatus(1); value.setVersionNo(value.getVersionNo() == null ? 1 : value.getVersionNo() + 1); value.setPublishedAt(System.currentTimeMillis()); profileService.updateById(value); return WebResponse.OK(value);
    }
    private AgentProductProfile required(String id) { AgentProductProfile value = profileService.getById(id); if (value == null || Boolean.TRUE.equals(value.getDeleted())) throw new ServerException(404, "Agent 产品配置不存在"); return value; }
    private void validate(AgentProductProfile value) {
        if (StringUtils.isBlank(value.getApplicationId())) value.setApplicationId(AgentApplicationService.PLATFORM_APPLICATION_ID);
        applicationService.requireActive(value.getApplicationId());
        if (!Arrays.asList("CUSTOMER_SERVICE", "KNOWLEDGE_QA", "BUSINESS_ASSISTANT").contains(value.getProductType())) throw new ServerException(422, "不支持的 Agent 产品类型");
        if (StringUtils.isBlank(value.getAgentDefinitionId()) || StringUtils.isBlank(value.getName())) throw new ServerException(422, "Agent 和名称不能为空");
        AgentDefinition agent = agentService.getById(value.getAgentDefinitionId());
        if (agent == null || Boolean.TRUE.equals(agent.getDeleted()) || !value.getApplicationId().equals(agent.getApplicationId())) throw new ServerException(422, "Agent 不属于指定业务应用空间");
        if (StringUtils.isBlank(value.getInputSchema()) || StringUtils.isBlank(value.getOutputSchema())) throw new ServerException(422, "输入和输出 Schema 不能为空");
    }
}
