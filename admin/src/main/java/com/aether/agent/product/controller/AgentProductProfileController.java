package com.aether.agent.product.controller;

import com.aether.agent.application.service.AgentApplicationService;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.product.dto.AgentProductProfileDto;
import com.aether.agent.product.entity.AgentProductProfile;
import com.aether.agent.product.entity.AgentProductProfileVersion;
import com.aether.agent.product.service.AgentProductProfileService;
import com.aether.agent.product.service.AgentProductProfileVersionService;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.permission.Permission;
import com.aether.local.CurrentUser;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;
import java.util.List;

/** 智能客服、智能问答与业务助手的可发布产品配置。 */
@RestController
@RequestMapping("/api/agent/product-profile")
@Permission(path = "/agent/product-profile")
public class AgentProductProfileController {
    private final AgentProductProfileService profileService;
    private final AgentDefinitionService agentService;
    private final AgentApplicationService applicationService;
    private final AgentProductProfileVersionService versionService;
    public AgentProductProfileController(AgentProductProfileService profileService, AgentDefinitionService agentService, AgentApplicationService applicationService, AgentProductProfileVersionService versionService) {
        this.profileService = profileService; this.agentService = agentService; this.applicationService = applicationService; this.versionService = versionService;
    }
    @GetMapping
    public WebResponse<List<AgentProductProfile>> list(@RequestParam(required = false) String applicationId) {
        return WebResponse.OK(profileService.list(Wrappers.lambdaQuery(AgentProductProfile.class)
                .eq(StringUtils.isNotBlank(applicationId), AgentProductProfile::getApplicationId, applicationId)
                .eq(AgentProductProfile::getDeleted, false).orderByDesc(AgentProductProfile::getUpdatedAt)));
    }
    @PostMapping
    @Permission(path = "/agent/product-profile", type = Permission.Type.Write)
    public WebResponse<String> create(@RequestBody AgentProductProfileDto dto) {
        AgentProductProfile value = new AgentProductProfile(); BeanUtils.copyProperties(dto, value); validate(value);
        value.setStatus(0); value.setVersionNo(0); profileService.save(value); return WebResponse.OK("创建成功", value.getId());
    }
    @PutMapping("/{id}")
    @Permission(path = "/agent/product-profile", type = Permission.Type.Write)
    public WebResponse<Void> update(@PathVariable String id, @RequestBody AgentProductProfileDto dto) {
        AgentProductProfile value = required(id);
        if (Integer.valueOf(1).equals(value.getStatus())) throw new ServerException(409, "已发布产品不可直接编辑，请复制后创建新草稿");
        BeanUtils.copyProperties(dto, value); validate(value); profileService.updateById(value); return WebResponse.OK("更新成功");
    }
    @PostMapping("/{id}/publish")
    @Permission(path = "/agent/product-profile", type = Permission.Type.Write)
    public WebResponse<AgentProductProfile> publish(@PathVariable String id) {
        AgentProductProfile value = required(id); validate(value);
        int nextVersion = value.getVersionNo() == null ? 1 : value.getVersionNo() + 1;
        long now = System.currentTimeMillis();
        AgentProductProfileVersion snapshot = new AgentProductProfileVersion();
        snapshot.setProfileId(value.getId()); snapshot.setVersionNo(nextVersion); snapshot.setSnapshot(JSON.toJSONString(value));
        snapshot.setPublishedBy(CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("userId")); snapshot.setPublishedAt(now);
        versionService.save(snapshot);
        value.setStatus(1); value.setVersionNo(nextVersion); value.setPublishedAt(now); profileService.updateById(value); return WebResponse.OK(value);
    }
    @PostMapping("/{id}/copy")
    @Permission(path = "/agent/product-profile", type = Permission.Type.Write)
    public WebResponse<String> copy(@PathVariable String id) {
        AgentProductProfile source = required(id);
        AgentProductProfile draft = new AgentProductProfile(); BeanUtils.copyProperties(source, draft);
        draft.setId(null); draft.setName(source.getName() + "（新草稿）"); draft.setStatus(0); draft.setVersionNo(0); draft.setPublishedAt(null);
        profileService.save(draft); return WebResponse.OK("已创建新草稿", draft.getId());
    }
    @GetMapping("/{id}/versions")
    public WebResponse<List<AgentProductProfileVersion>> versions(@PathVariable String id) {
        required(id);
        return WebResponse.OK(versionService.list(Wrappers.lambdaQuery(AgentProductProfileVersion.class)
                .eq(AgentProductProfileVersion::getProfileId, id).eq(AgentProductProfileVersion::getDeleted, false)
                .orderByDesc(AgentProductProfileVersion::getVersionNo)));
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
