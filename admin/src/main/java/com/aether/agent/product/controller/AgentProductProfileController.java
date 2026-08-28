package com.aether.agent.product.controller;

import com.aether.agent.application.service.AgentApplicationService;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.product.dto.AgentProductProfileDto;
import com.aether.agent.product.dto.AgentProductProfileQueryDto;
import com.aether.agent.product.entity.AgentProductProfile;
import com.aether.agent.product.entity.AgentProductProfileVersion;
import com.aether.agent.product.service.AgentProductProfileService;
import com.aether.agent.product.service.AgentProductProfileVersionService;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.AgentToolBindingService;
import com.aether.agent.service.AgentKnowledgeBaseBindingService;
import com.aether.agent.entity.AgentToolBinding;
import com.aether.agent.entity.AgentKnowledgeBaseBinding;
import com.aether.sys.entity.User;
import com.aether.sys.service.UserService;
import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.entity.WebResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import com.aether.exception.ServerException;
import com.aether.permission.Permission;
import com.aether.local.CurrentUser;
import com.aether.i18n.I18nUtils;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/** 将 Agent 或工作流包装为可对外交付的产品。 */
@RestController
@Api(tags = "Agent 产品配置 API")
@RequestMapping("/api/agent/product-profile")
@Permission(path = "/agent/product-profile")
public class AgentProductProfileController {
    private final AgentProductProfileService profileService;
    private final AgentDefinitionService agentService;
    private final AgentWorkflowService workflowService;
    private final AgentApplicationService applicationService;
    private final AgentProductProfileVersionService versionService;
    private final UserService userService;
    private final AgentToolBindingService toolBindingService;
    private final AgentKnowledgeBaseBindingService knowledgeBindingService;
    public AgentProductProfileController(AgentProductProfileService profileService, AgentDefinitionService agentService, AgentWorkflowService workflowService, AgentApplicationService applicationService, AgentProductProfileVersionService versionService, UserService userService,
                                         AgentToolBindingService toolBindingService, AgentKnowledgeBaseBindingService knowledgeBindingService) {
        this.profileService = profileService; this.agentService = agentService; this.workflowService = workflowService; this.applicationService = applicationService; this.versionService = versionService; this.userService = userService;
        this.toolBindingService = toolBindingService; this.knowledgeBindingService = knowledgeBindingService;
    }
    @ApiOperation("查询 Agent 产品配置列表")
    @PostMapping("/list")
    public WebResponse<List<AgentProductProfile>> list(@RequestBody(required = false) AgentProductProfileQueryDto query) {
        long current = query == null || query.getCurrent() == null ? 1L : query.getCurrent();
        long pageSize = query == null || query.getPageSize() == null ? 20L : query.getPageSize();
        Page<AgentProductProfile> page = profileService.page(new Page<AgentProductProfile>(Math.max(1L, current), Math.min(Math.max(1L, pageSize), 100L)), Wrappers.lambdaQuery(AgentProductProfile.class)
                .eq(query != null && StringUtils.isNotBlank(query.getApplicationId()), AgentProductProfile::getApplicationId, query == null ? null : query.getApplicationId())
                .like(query != null && StringUtils.isNotBlank(query.getName()), AgentProductProfile::getName, query == null ? null : query.getName())
                .eq(query != null && StringUtils.isNotBlank(query.getProductType()), AgentProductProfile::getProductType, query == null ? null : query.getProductType())
                .eq(query != null && query.getStatus() != null, AgentProductProfile::getStatus, query == null ? null : query.getStatus())
                .eq(AgentProductProfile::getDeleted, false).orderByDesc(AgentProductProfile::getUpdatedAt));
        return WebResponse.Page(page.getRecords(), page.getTotal());
    }
    @ApiOperation("创建 Agent 产品配置")
    @PostMapping
    @Permission(path = "/agent/product-profile", type = Permission.Type.Write)
    public WebResponse<String> create(@RequestBody AgentProductProfileDto dto) {
        AgentProductProfile value = new AgentProductProfile(); BeanUtils.copyProperties(dto, value); validate(value);
        value.setProductId(UUID.randomUUID().toString().replace("-", ""));
        value.setStatus(0); value.setVersionNo(1); profileService.save(value); return WebResponse.OK("创建成功", value.getId());
    }
    @ApiOperation("更新 Agent 产品配置")
    @PutMapping("/{id}")
    @Permission(path = "/agent/product-profile", type = Permission.Type.Write)
    public WebResponse<Void> update(@PathVariable String id, @RequestBody AgentProductProfileDto dto) {
        AgentProductProfile value = required(id);
        if (Integer.valueOf(1).equals(value.getStatus()) || Integer.valueOf(2).equals(value.getStatus())) throw new ServerException(409, I18nUtils.getMessage("agent.product.published.edit.forbidden"));
        BeanUtils.copyProperties(dto, value); validate(value); profileService.updateById(value); return WebResponse.OK("更新成功");
    }
    @ApiOperation("发布 Agent 产品配置")
    @PostMapping("/{id}/publish")
    @Permission(path = "/agent/product-profile", type = Permission.Type.Write)
    public WebResponse<AgentProductProfile> publish(@PathVariable String id) {
        AgentProductProfile value = required(id); validate(value);
        if (Integer.valueOf(1).equals(value.getStatus())) throw new ServerException(409, I18nUtils.getMessage("agent.product.published.edit.forbidden"));
        int nextVersion = nextVersion(value.getProductId());
        long now = System.currentTimeMillis();
        AgentProductProfileVersion snapshot = new AgentProductProfileVersion();
        snapshot.setProfileId(value.getId()); snapshot.setVersionNo(nextVersion); snapshot.setSnapshot(executableSnapshot(value));
        snapshot.setPublishedBy(publisherName()); snapshot.setPublishedAt(now);
        versionService.save(snapshot);
        value.setStatus(1); value.setVersionNo(nextVersion); value.setPublishedAt(now); value.setPublishedSnapshotId(snapshot.getId());
        profileService.updateById(value); return WebResponse.OK(value);
    }
    @ApiOperation("复制 Agent 产品配置")
    @PostMapping("/{id}/copy")
    @Permission(path = "/agent/product-profile", type = Permission.Type.Write)
    public WebResponse<String> copy(@PathVariable String id) {
        AgentProductProfile source = required(id);
        AgentProductProfile draft = new AgentProductProfile(); BeanUtils.copyProperties(source, draft);
        draft.setId(null); draft.setCode(null); draft.setProductId(StringUtils.defaultIfBlank(source.getProductId(), source.getId()));
        draft.setStatus(0); draft.setVersionNo(nextVersion(draft.getProductId())); draft.setPublishedAt(null); draft.setPublishedSnapshotId(null);
        validate(draft);
        profileService.save(draft); return WebResponse.OK("已创建新草稿", draft.getId());
    }
    @ApiOperation("设置 Agent 产品配置启用状态")
    @PostMapping("/{id}/enabled")
    @Permission(path = "/agent/product-profile", type = Permission.Type.Write)
    public WebResponse<Void> setEnabled(@PathVariable String id, @RequestParam boolean enabled) {
        AgentProductProfile value = required(id);
        if (!Integer.valueOf(1).equals(value.getStatus()) && !Integer.valueOf(2).equals(value.getStatus()))
            throw new ServerException(409, I18nUtils.getMessage("agent.product.lifecycle.invalid"));
        value.setStatus(enabled ? 1 : 2);
        profileService.updateById(value);
        return WebResponse.OK((Void) null);
    }
    @ApiOperation("删除 Agent 产品配置")
    @DeleteMapping("/{id}")
    @Permission(path = "/agent/product-profile", type = Permission.Type.Write)
    public WebResponse<Void> delete(@PathVariable String id) {
        AgentProductProfile value = required(id);
        if (Integer.valueOf(1).equals(value.getStatus())) throw new ServerException(409, I18nUtils.getMessage("agent.product.delete.published.forbidden"));
        profileService.removeById(value.getId());
        return WebResponse.OK((Void) null);
    }
    @ApiOperation("查询 Agent 产品配置版本")
    @GetMapping("/{id}/versions")
    public WebResponse<List<AgentProductProfileVersion>> versions(@PathVariable String id) {
        required(id);
        List<String> profileIds = profileService.list(Wrappers.lambdaQuery(AgentProductProfile.class)
                .eq(AgentProductProfile::getProductId, StringUtils.defaultIfBlank(required(id).getProductId(), id))
                .eq(AgentProductProfile::getDeleted, false)).stream().map(AgentProductProfile::getId).collect(java.util.stream.Collectors.toList());
        List<AgentProductProfileVersion> versions = versionService.list(Wrappers.lambdaQuery(AgentProductProfileVersion.class)
                .in(AgentProductProfileVersion::getProfileId, profileIds).eq(AgentProductProfileVersion::getDeleted, false)
                .orderByDesc(AgentProductProfileVersion::getVersionNo));
        for (AgentProductProfileVersion version : versions) version.setPublishedBy(publisherName(version.getPublishedBy()));
        return WebResponse.OK(versions);
    }
    private AgentProductProfile required(String id) { AgentProductProfile value = profileService.getById(id); if (value == null || Boolean.TRUE.equals(value.getDeleted())) throw new ServerException(404, I18nUtils.getMessage("agent.product.not-found")); return value; }
    private String publisherName() { return publisherName(CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("userId")); }
    private String publisherName(String userId) {
        if (StringUtils.isBlank(userId)) return "-";
        User user = userService.getById(userId);
        return user == null || StringUtils.isBlank(user.getUsername()) ? userId : user.getUsername();
    }
    private int nextVersion(String productId) {
        List<AgentProductProfile> versions = profileService.list(Wrappers.lambdaQuery(AgentProductProfile.class)
                .eq(AgentProductProfile::getProductId, productId).eq(AgentProductProfile::getStatus, 1)
                .eq(AgentProductProfile::getDeleted, false));
        int max = 0;
        for (AgentProductProfile version : versions) max = Math.max(max, version.getVersionNo() == null ? 0 : version.getVersionNo());
        return max + 1;
    }
    private void validate(AgentProductProfile value) {
        if (StringUtils.isBlank(value.getApplicationId())) value.setApplicationId(AgentApplicationService.PLATFORM_APPLICATION_ID);
        applicationService.requireActive(value.getApplicationId());
        if (!"AGENT".equals(value.getProductType()) && !"WORKFLOW".equals(value.getProductType())) throw new ServerException(422, I18nUtils.getMessage("agent.product.type.unsupported"));
        if (StringUtils.isBlank(value.getName())) throw new ServerException(422, I18nUtils.getMessage("agent.product.name.required"));
        if (StringUtils.isBlank(value.getCode())) value.setCode("product_" + java.util.UUID.randomUUID().toString().replace("-", ""));
        if (!value.getCode().matches("[A-Za-z][A-Za-z0-9_-]{2,63}")) throw new ServerException(422, I18nUtils.getMessage("agent.product.code.invalid"));
        boolean agentProduct = "AGENT".equals(value.getProductType());
        if (agentProduct && StringUtils.isBlank(value.getAgentDefinitionId()) || !agentProduct && StringUtils.isBlank(value.getWorkflowId()))
            throw new ServerException(422, I18nUtils.getMessage("agent.product.target.required"));
        if (agentProduct) {
            value.setWorkflowId(null);
            AgentDefinition agent = agentService.getById(value.getAgentDefinitionId());
            if (agent == null || Boolean.TRUE.equals(agent.getDeleted()) || !value.getApplicationId().equals(agent.getApplicationId())) throw new ServerException(422, I18nUtils.getMessage("agent.product.agent.application.mismatch"));
        } else {
            value.setAgentDefinitionId(null);
            AgentWorkflow workflow = workflowService.getById(value.getWorkflowId());
            if (workflow == null || Boolean.TRUE.equals(workflow.getDeleted()) || !value.getApplicationId().equals(workflow.getApplicationId())) throw new ServerException(422, I18nUtils.getMessage("agent.product.workflow.application.mismatch"));
        }
        if (StringUtils.isBlank(value.getInputSchema())) value.setInputSchema(agentProduct
                ? "{\"type\":\"object\",\"required\":[\"input\"],\"properties\":{\"input\":{\"type\":\"string\"}}}"
                : "{\"type\":\"object\",\"required\":[\"businessId\",\"input\"],\"properties\":{\"businessId\":{\"type\":\"string\"},\"businessType\":{\"type\":\"string\"},\"input\":{\"type\":\"object\"}}}");
        if (StringUtils.isBlank(value.getOutputSchema())) value.setOutputSchema("{\"type\":\"object\"}");
        if (StringUtils.isBlank(value.getApiProtocolVersion())) value.setApiProtocolVersion(agentProduct ? "conversation-api-v1" : "workflow-api-v1");
        if (agentProduct && !"conversation-api-v1".equals(value.getApiProtocolVersion()))
            throw new ServerException(422, "Agent 产品仅支持 conversation-api-v1");
        if (StringUtils.isNotBlank(value.getAllowedContextKeys())) {
            try {
                Object declaration = JSON.parse(value.getAllowedContextKeys());
                if (!(declaration instanceof com.alibaba.fastjson2.JSONObject) && !(declaration instanceof com.alibaba.fastjson2.JSONArray))
                    throw new IllegalArgumentException("context 声明必须为对象或数组");
            } catch (RuntimeException ex) {
                throw new ServerException(422, "allowedContextKeys 必须是 JSON 对象或数组");
            }
        }
    }

    private String executableSnapshot(AgentProductProfile product) {
        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<String, Object>();
        snapshot.put("product", product);
        snapshot.put("protocolVersion", product.getApiProtocolVersion());
        if ("AGENT".equals(product.getProductType())) {
            snapshot.put("agent", agentService.getById(product.getAgentDefinitionId()));
            snapshot.put("toolBindings", toolBindingService.list(Wrappers.lambdaQuery(AgentToolBinding.class)
                    .eq(AgentToolBinding::getAgentDefinitionId, product.getAgentDefinitionId()).eq(AgentToolBinding::getDeleted, false)));
            snapshot.put("knowledgeBindings", knowledgeBindingService.list(Wrappers.lambdaQuery(AgentKnowledgeBaseBinding.class)
                    .eq(AgentKnowledgeBaseBinding::getAgentDefinitionId, product.getAgentDefinitionId()).eq(AgentKnowledgeBaseBinding::getDeleted, false)));
        } else snapshot.put("agent", null);
        snapshot.put("workflow", "WORKFLOW".equals(product.getProductType()) ? workflowService.getById(product.getWorkflowId()) : null);
        return JSON.toJSONString(snapshot);
    }
}
