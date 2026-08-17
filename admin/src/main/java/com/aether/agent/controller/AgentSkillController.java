package com.aether.agent.controller;

import com.aether.agent.skill.dto.AgentSkillDraftDto;
import com.aether.agent.skill.dto.SkillRoutingConfigDto;
import com.aether.agent.skill.dto.AgentSkillPreviewDto;
import com.aether.agent.skill.dto.AgentSkillResourceGenerateDto;
import com.aether.agent.skill.entity.AgentSkill;
import com.aether.agent.skill.entity.AgentSkillResource;
import com.aether.agent.skill.entity.AgentSkillVersion;
import com.aether.agent.skill.service.AgentSkillService;
import com.aether.agent.skill.service.SkillResourceWorkbenchService;
import com.aether.agent.skill.service.SkillRouterService;
import com.aether.agent.skill.service.SkillRouteDecision;
import com.aether.agent.skill.service.SkillRoutingConfigService;
import com.aether.agent.skill.service.SkillRoutingIndexService;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.ModelProviderService;
import com.aether.agent.service.ModelCatalogService;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.skill.vo.AgentSkillDetailVo;
import com.aether.agent.skill.vo.AgentSkillPreviewVo;
import com.aether.agent.skill.vo.AgentSkillPublishCheckVo;
import com.aether.agent.skill.vo.AgentSkillVo;
import com.aether.agent.skill.vo.AgentSkillStatisticsVo;
import com.aether.agent.skill.vo.AgentSkillResourceGenerateVo;
import com.aether.entity.Option;
import com.aether.entity.WebResponse;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.permission.Permission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Skill 管理接口，仅管理员可维护草稿、版本和启停状态。
 */
@Validated
@RestController
@Permission(path = "/agent/skill")
@RequestMapping("/api/agent/skill")
public class AgentSkillController {
    private final AgentSkillService skillService;
    private final SkillResourceWorkbenchService resourceWorkbenchService;
    private final SkillRouterService skillRouterService;
    private final AgentDefinitionService agentDefinitionService;
    private final ModelProviderService modelProviderService;
    private final ModelCatalogService modelCatalogService;
    private final SkillRoutingConfigService routingConfigService;
    private final SkillRoutingIndexService routingIndexService;

    /**
     * 创建 {@code AgentSkillController} 实例。
     */
    @Autowired
    public AgentSkillController(AgentSkillService skillService, SkillResourceWorkbenchService resourceWorkbenchService, SkillRouterService skillRouterService, AgentDefinitionService agentDefinitionService, ModelProviderService modelProviderService, ModelCatalogService modelCatalogService, SkillRoutingConfigService routingConfigService, SkillRoutingIndexService routingIndexService) {
        this.skillService = skillService;
        this.resourceWorkbenchService = resourceWorkbenchService;
        this.skillRouterService = skillRouterService;
        this.agentDefinitionService = agentDefinitionService;
        this.modelProviderService = modelProviderService;
        this.modelCatalogService = modelCatalogService;
        this.routingConfigService = routingConfigService;
        this.routingIndexService = routingIndexService;
    }

    /**
     * Test/backward-compatible constructor; Spring uses the complete dependency constructor.
     */
    public AgentSkillController(AgentSkillService skillService, SkillResourceWorkbenchService resourceWorkbenchService) {
        this(skillService, resourceWorkbenchService, null, null, null, null, null, null);
    }

    /**
     * 查询当前请求。
     */
    @PostMapping("/list")
    public WebResponse<List<AgentSkillVo>> list(@RequestBody AgentSkillVo query) {
        Page<AgentSkill> page = skillService.page(new Page<>(query.getCurrent(), query.getPageSize()), Wrappers.lambdaQuery(AgentSkill.class)
                .like(StringUtils.isNotBlank(query.getName()), AgentSkill::getName, query.getName())
                .like(StringUtils.isNotBlank(query.getCode()), AgentSkill::getCode, query.getCode())
                .eq(StringUtils.isNotBlank(query.getCategory()), AgentSkill::getCategory, query.getCategory())
                .eq(query.getStatus() != null, AgentSkill::getStatus, query.getStatus())
                .orderByDesc(AgentSkill::getCreatedAt));
        List<AgentSkillVo> records = page.getRecords().stream().map(item -> {
            AgentSkillVo vo = skillService.lifecycle(item);
            if (vo == null) {
                vo = new AgentSkillVo();
                org.springframework.beans.BeanUtils.copyProperties(item, vo);
            }
            return vo;
        }).collect(Collectors.toList());
        return WebResponse.Page(records, page.getTotal());
    }

    /**
     * 处理options。
     */
    @GetMapping("/options")
    @Permission(required = false)
    public WebResponse<List<Option>> options() {
        List<Option> os = skillService.list(Wrappers.lambdaQuery(AgentSkill.class)
                        .eq(AgentSkill::getDeleted, false)
                        .orderByAsc(AgentSkill::getName))
                .stream().map(item -> {
                    Option option = new Option(item.getName(), item.getId());
                    option.setCode(item.getCode());
                    option.setStatus(item.getStatus());
                    return option;
                }).collect(Collectors.toList());
        return WebResponse.OK(os);
    }

    /**
     * 处理statistics。
     */
    @GetMapping("/statistics")
    public WebResponse<AgentSkillStatisticsVo> statistics() {
        return WebResponse.OK(skillService.statistics());
    }

    /**
     * 详情当前请求。
     */
    @GetMapping("/{id}")
    public WebResponse<AgentSkillDetailVo> detail(@PathVariable @NotBlank String id) {
        return WebResponse.OK(skillService.detail(id));
    }

    /**
     * 创建当前请求。
     */
    @PostMapping
    @Permission(path = "/agent/skill", type = Permission.Type.Write)
    public WebResponse<String> create(@RequestBody AgentSkillDraftDto dto) {
        return WebResponse.OK(I18nUtils.getMessage("skill.draft.create.success"), skillService.createDraft(dto));
    }

    /**
     * 更新Draft。
     */
    @PutMapping("/{id}")
    @Permission(path = "/agent/skill", type = Permission.Type.Write)
    public WebResponse<Void> updateDraft(@PathVariable @NotBlank String id, @RequestBody AgentSkillDraftDto dto) {
        skillService.updateDraft(id, dto);
        return WebResponse.OK(I18nUtils.getMessage("skill.draft.update.success"));
    }

    /**
     * 创建下一个Draft。
     */
    @PostMapping("/{id}/draft")
    @Permission(path = "/agent/skill", type = Permission.Type.Write)
    public WebResponse<String> createNextDraft(@PathVariable @NotBlank String id) {
        return WebResponse.OK(I18nUtils.getMessage("skill.draft.create.success"), skillService.createNextDraft(id));
    }

    /**
     * 发布当前请求。
     */
    @PostMapping("/{id}/versions/{versionId}/publish")
    @Permission(path = "/agent/skill", type = Permission.Type.Write)
    public WebResponse<AgentSkillVersion> publish(@PathVariable @NotBlank String id, @PathVariable @NotBlank String versionId) {
        AgentSkillDetailVo detail = skillService.detail(id);
        if (detail.getDraft() == null || !versionId.equals(detail.getDraft().getId()))
            throw new IllegalArgumentException(I18nUtils.getMessage("skill.draft.publish.current.required"));
        String userId = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("userId");
        return WebResponse.OK(skillService.publish(id, userId));
    }

    /**
     * 发布检查。
     */
    @GetMapping("/{id}/publish-check")
    public WebResponse<AgentSkillPublishCheckVo> publishCheck(@PathVariable @NotBlank String id) {
        return WebResponse.OK(skillService.publishCheck(id));
    }

    /**
     * 处理versions。
     */
    @GetMapping("/{id}/versions")
    public WebResponse<List<AgentSkillVersion>> versions(@PathVariable @NotBlank String id) {
        return WebResponse.OK(skillService.listVersions(id));
    }

    /**
     * 状态当前请求。
     */
    @PutMapping("/{id}/status")
    @Permission(path = "/agent/skill", type = Permission.Type.Write)
    public WebResponse<Void> status(@PathVariable @NotBlank String id, @RequestBody AgentSkillVo dto) {
        AgentSkill skill = skillService.getById(id);
        if (skill == null) throw new IllegalArgumentException(I18nUtils.getMessage("skill.not-found"));
        skill.setStatus(dto.getStatus());
        skillService.updateById(skill);
        return WebResponse.OK(I18nUtils.getMessage("skill.status.update.success"));
    }

    /**
     * 上传资源。
     */
    @PostMapping("/{id}/resources")
    @Permission(path = "/agent/skill", type = Permission.Type.Write)
    public WebResponse<AgentSkillResource> uploadResource(@PathVariable @NotBlank String id,
                                                          @RequestParam("file") MultipartFile file,
                                                          @RequestParam(value = "purpose", required = false) String purpose,
                                                          @RequestParam(value = "type", required = false) String type) throws IOException {
        return WebResponse.OK(skillService.uploadResource(id, file.getOriginalFilename(), file.getContentType(), file.getBytes(), purpose, type));
    }

    /**
     * 更新资源。
     */
    @PutMapping("/{id}/resources/{resourceId}")
    @Permission(path = "/agent/skill", type = Permission.Type.Write)
    public WebResponse<AgentSkillResource> updateResource(@PathVariable @NotBlank String id, @PathVariable @NotBlank String resourceId,
                                                          @RequestParam("file") MultipartFile file,
                                                          @RequestParam(value = "purpose", required = false) String purpose,
                                                          @RequestParam(value = "type", required = false) String type) throws IOException {
        return WebResponse.OK(skillService.updateDraftResource(id, resourceId, file.getOriginalFilename(), file.getContentType(), file.getBytes(), purpose, type));
    }

    /**
     * 处理resources。
     */
    @GetMapping("/{id}/resources")
    public WebResponse<List<AgentSkillResource>> resources(@PathVariable @NotBlank String id) {
        return WebResponse.OK(skillService.listResources(id));
    }

    /**
     * 资源Content。
     */
    @GetMapping("/{id}/resources/{resourceId}/content")
    public WebResponse<String> resourceContent(@PathVariable @NotBlank String id, @PathVariable @NotBlank String resourceId) {
        // Keep the file body in data.  Passing a String to the single-argument
        // overload would otherwise select OK(String message), leaving data empty.
        return WebResponse.OK(null, resourceWorkbenchService.content(id, resourceId));
    }

    /**
     * 生成资源。
     */
    @PostMapping("/{id}/resources/generate")
    @Permission(path = "/agent/skill", type = Permission.Type.Write)
    public WebResponse<AgentSkillResourceGenerateVo> generateResource(@PathVariable @NotBlank String id,
                                                                      @RequestBody AgentSkillResourceGenerateDto dto) {
        return WebResponse.OK(resourceWorkbenchService.generate(id, dto));
    }

    /**
     * 移除资源。
     */
    @DeleteMapping("/{id}/resources/{resourceId}")
    @Permission(path = "/agent/skill", type = Permission.Type.Write)
    public WebResponse<Void> removeResource(@PathVariable @NotBlank String id, @PathVariable @NotBlank String resourceId) {
        skillService.removeDraftResource(id, resourceId);
        return WebResponse.OK(I18nUtils.getMessage("skill.resource.remove.success"));
    }

    /**
     * 预览当前请求。
     */
    @PostMapping("/{id}/preview")
    public WebResponse<AgentSkillPreviewVo> preview(@PathVariable @NotBlank String id, @RequestBody AgentSkillPreviewDto dto) {
        return WebResponse.OK(skillService.preview(id, dto));
    }

    /**
     * Preview discovery only; it never loads full instructions or invokes the answer path.
     */
    @PostMapping("/routing-preview")
    public WebResponse<SkillRouteDecision> routingPreview(@RequestParam @NotBlank String agentId, @RequestParam @NotBlank String query) {
        AgentDefinition agent = agentDefinitionService.getById(agentId);
        if (agent == null) throw new IllegalArgumentException(I18nUtils.getMessage("skill.agent.not-found"));
        ModelProvider provider = "DEEP".equalsIgnoreCase(agent.getExecutionMode()) || modelCatalogService == null
                ? null : modelCatalogService.resolveProvider(agent.getModelId(), "CHAT,MULTIMODAL");
        return WebResponse.OK(skillRouterService.route(agent, provider, query, skillService.listBindings(agentId).stream().filter(binding -> Integer.valueOf(1).equals(binding.getStatus())).collect(Collectors.toList())));
    }

    /**
     * 处理routing配置。
     */
    @GetMapping("/routing-config")
    public WebResponse<SkillRoutingConfigDto> routingConfig() {
        return WebResponse.OK(routingConfigService.get());
    }

    /**
     * 更新Routing配置。
     */
    @PutMapping("/routing-config")
    @Permission(path = "/agent/skill", type = Permission.Type.Write)
    public WebResponse<Void> updateRoutingConfig(@RequestBody SkillRoutingConfigDto dto) {
        routingConfigService.update(dto);
        routingIndexService.reindexPublishedVersions();
        return WebResponse.OK(I18nUtils.getMessage("skill.routing.config.update.success"));
    }

}
