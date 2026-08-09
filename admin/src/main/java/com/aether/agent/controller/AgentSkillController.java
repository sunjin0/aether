package com.aether.agent.controller;

import com.aether.agent.skill.dto.AgentSkillDraftDto;
import com.aether.agent.skill.dto.AgentSkillPreviewDto;
import com.aether.agent.skill.dto.AgentSkillExecutionConfigDto;
import com.aether.agent.skill.dto.AgentSkillResourceGenerateDto;
import com.aether.agent.skill.entity.AgentSkill;
import com.aether.agent.skill.entity.AgentSkillResource;
import com.aether.agent.skill.entity.AgentSkillVersion;
import com.aether.agent.skill.entity.AgentSkillExecutionConfig;
import com.aether.agent.skill.service.AgentSkillService;
import com.aether.agent.skill.service.SkillResourceWorkbenchService;
import com.aether.agent.skill.vo.AgentSkillDetailVo;
import com.aether.agent.skill.vo.AgentSkillPreviewVo;
import com.aether.agent.skill.vo.AgentSkillPublishCheckVo;
import com.aether.agent.skill.vo.AgentSkillVo;
import com.aether.agent.skill.vo.AgentSkillStatisticsVo;
import com.aether.agent.skill.vo.AgentSkillResourceGenerateVo;
import com.aether.entity.Option;
import com.aether.entity.WebResponse;
import com.aether.local.CurrentUser;
import com.aether.permission.Permission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/** Skill 管理接口，仅管理员可维护草稿、版本和启停状态。 */
@Validated
@RestController
@Permission(path = "/agent/skill")
@RequestMapping("/api/agent/skill")
public class AgentSkillController {
    private final AgentSkillService skillService;
    private final SkillResourceWorkbenchService resourceWorkbenchService;

    public AgentSkillController(AgentSkillService skillService, SkillResourceWorkbenchService resourceWorkbenchService) {
        this.skillService = skillService;
        this.resourceWorkbenchService = resourceWorkbenchService;
    }

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
            if (vo == null) { vo = new AgentSkillVo(); org.springframework.beans.BeanUtils.copyProperties(item, vo); }
            return vo;
        }).collect(Collectors.toList());
        return WebResponse.Page(records, page.getTotal());
    }

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

    @GetMapping("/statistics")
    public WebResponse<AgentSkillStatisticsVo> statistics() { return WebResponse.OK(skillService.statistics()); }

    @GetMapping("/{id}")
    public WebResponse<AgentSkillDetailVo> detail(@PathVariable @NotBlank String id) { return WebResponse.OK(skillService.detail(id)); }

    @PostMapping
    @Permission(path = "/agent/skill", type = Permission.Type.Write)
    public WebResponse<String> create(@RequestBody AgentSkillDraftDto dto) { return WebResponse.OK("Skill draft created", skillService.createDraft(dto)); }

    @PutMapping("/{id}")
    @Permission(path = "/agent/skill", type = Permission.Type.Write)
    public WebResponse<Void> updateDraft(@PathVariable @NotBlank String id, @RequestBody AgentSkillDraftDto dto) { skillService.updateDraft(id, dto); return WebResponse.OK("Skill draft updated"); }

    @PostMapping("/{id}/draft")
    @Permission(path = "/agent/skill", type = Permission.Type.Write)
    public WebResponse<String> createNextDraft(@PathVariable @NotBlank String id) { return WebResponse.OK("Skill draft created", skillService.createNextDraft(id)); }

    @PostMapping("/{id}/versions/{versionId}/publish")
    @Permission(path = "/agent/skill", type = Permission.Type.Write)
    public WebResponse<AgentSkillVersion> publish(@PathVariable @NotBlank String id, @PathVariable @NotBlank String versionId) {
        AgentSkillDetailVo detail = skillService.detail(id);
        if (detail.getDraft() == null || !versionId.equals(detail.getDraft().getId())) throw new IllegalArgumentException("Only the current draft can be published");
        String userId = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("userId");
        return WebResponse.OK(skillService.publish(id, userId));
    }

    @GetMapping("/{id}/publish-check")
    public WebResponse<AgentSkillPublishCheckVo> publishCheck(@PathVariable @NotBlank String id) {
        return WebResponse.OK(skillService.publishCheck(id));
    }

    @GetMapping("/{id}/versions")
    public WebResponse<List<AgentSkillVersion>> versions(@PathVariable @NotBlank String id) {
        return WebResponse.OK(skillService.listVersions(id));
    }

    @PutMapping("/{id}/status")
    @Permission(path = "/agent/skill", type = Permission.Type.Write)
    public WebResponse<Void> status(@PathVariable @NotBlank String id, @RequestBody AgentSkillVo dto) {
        AgentSkill skill = skillService.getById(id); if (skill == null) throw new IllegalArgumentException("Skill not found"); skill.setStatus(dto.getStatus()); skillService.updateById(skill); return WebResponse.OK("Skill status updated");
    }

    @PostMapping("/{id}/resources")
    @Permission(path = "/agent/skill", type = Permission.Type.Write)
    public WebResponse<AgentSkillResource> uploadResource(@PathVariable @NotBlank String id,
                                                          @RequestParam("file") MultipartFile file,
                                                          @RequestParam(value = "purpose", required = false) String purpose,
                                                          @RequestParam(value = "type", required = false) String type) throws IOException {
        return WebResponse.OK(skillService.uploadResource(id, file.getOriginalFilename(), file.getContentType(), file.getBytes(), purpose, type));
    }

    @PutMapping("/{id}/resources/{resourceId}")
    @Permission(path = "/agent/skill", type = Permission.Type.Write)
    public WebResponse<AgentSkillResource> updateResource(@PathVariable @NotBlank String id, @PathVariable @NotBlank String resourceId,
                                                           @RequestParam("file") MultipartFile file,
                                                           @RequestParam(value = "purpose", required = false) String purpose,
                                                           @RequestParam(value = "type", required = false) String type) throws IOException {
        return WebResponse.OK(skillService.updateDraftResource(id, resourceId, file.getOriginalFilename(), file.getContentType(), file.getBytes(), purpose, type));
    }

    @GetMapping("/{id}/resources")
    public WebResponse<List<AgentSkillResource>> resources(@PathVariable @NotBlank String id) { return WebResponse.OK(skillService.listResources(id)); }

    @GetMapping("/{id}/resources/{resourceId}/content")
    public WebResponse<String> resourceContent(@PathVariable @NotBlank String id, @PathVariable @NotBlank String resourceId) {
        // Keep the file body in data.  Passing a String to the single-argument
        // overload would otherwise select OK(String message), leaving data empty.
        return WebResponse.OK(null, resourceWorkbenchService.content(id, resourceId));
    }

    @PostMapping("/{id}/resources/generate")
    @Permission(path = "/agent/skill", type = Permission.Type.Write)
    public WebResponse<AgentSkillResourceGenerateVo> generateResource(@PathVariable @NotBlank String id,
                                                                       @RequestBody AgentSkillResourceGenerateDto dto) {
        return WebResponse.OK(resourceWorkbenchService.generate(id, dto));
    }

    @DeleteMapping("/{id}/resources/{resourceId}")
    @Permission(path = "/agent/skill", type = Permission.Type.Write)
    public WebResponse<Void> removeResource(@PathVariable @NotBlank String id, @PathVariable @NotBlank String resourceId) { skillService.removeDraftResource(id, resourceId); return WebResponse.OK("Skill resource removed"); }

    @PostMapping("/{id}/preview")
    public WebResponse<AgentSkillPreviewVo> preview(@PathVariable @NotBlank String id, @RequestBody AgentSkillPreviewDto dto) { return WebResponse.OK(skillService.preview(id, dto)); }

    @GetMapping("/{id}/execution-config")
    public WebResponse<AgentSkillExecutionConfig> executionConfig(@PathVariable @NotBlank String id) {
        return WebResponse.OK(skillService.executionConfig(id));
    }

    @PutMapping("/{id}/execution-config")
    @Permission(path = "/agent/skill", type = Permission.Type.Write)
    public WebResponse<Void> updateExecutionConfig(@PathVariable @NotBlank String id,
                                                    @RequestBody AgentSkillExecutionConfigDto dto) {
        skillService.updateExecutionConfig(id, dto);
        return WebResponse.OK("Skill execution configuration updated");
    }

}
