package com.aether.evaluation.controller;

import com.aether.entity.WebResponse;
import com.aether.evaluation.entity.EvaluationPolicy;
import com.aether.evaluation.service.EvaluationPolicyService;
import com.aether.evaluation.service.EvaluationGateService;
import com.aether.permission.Permission;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.sys.service.AccountDataScopeService;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.skill.entity.AgentSkill;
import com.aether.agent.skill.service.AgentSkillService;
import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.exception.ServerException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;

@Api(tags = "评测发布门禁 API")
@RestController
@Permission(path = "/agent/run")
@RequestMapping("/api/agent/evaluation/policy")
public class EvaluationPolicyController {
    private final EvaluationPolicyService service;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AgentDefinitionService agentService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AgentWorkflowService workflowService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AgentSkillService skillService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AccountDataScopeService dataScopeService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private EvaluationGateService gateService;

    public EvaluationPolicyController(EvaluationPolicyService service) { this.service = service; }

    @ApiOperation("查询评测发布策略")
    @GetMapping
    public WebResponse<EvaluationPolicy> get(@RequestParam @NotBlank String targetType,
                                             @RequestParam @NotBlank String targetId) {
        assertTargetReadable(targetType, targetId);
        return WebResponse.OK(service.getOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<EvaluationPolicy>()
                .eq("target_type", targetType).eq("target_id", targetId).in(dataScopeService != null, "created_by", readableCreatorIds()).eq("deleted", false), false));
    }

    @ApiOperation("保存评测发布策略")
    @PostMapping
    @Permission(path = "/agent/run", type = Permission.Type.Write)
    public WebResponse<String> save(@RequestBody EvaluationPolicy request) {
        if (request == null || StringUtils.isBlank(request.getTargetType()) || StringUtils.isBlank(request.getTargetId()))
            return WebResponse.Error(400, I18nUtils.getMessage("agent.evaluation.policy.target.required"));
        if (!"WORKFLOW".equals(request.getTargetType()) && !"AGENT".equals(request.getTargetType()) && !"SKILL".equals(request.getTargetType()))
            return WebResponse.Error(400, I18nUtils.getMessage("agent.evaluation.policy.target-type.invalid"));
        assertTargetReadable(request.getTargetType(), request.getTargetId());
        if (request.getMinimumScore() != null && (request.getMinimumScore() < 0 || request.getMinimumScore() > 100))
            return WebResponse.Error(400, I18nUtils.getMessage("agent.evaluation.policy.minimum-score.invalid"));
        if (request.getMinimumPassRate() != null && (request.getMinimumPassRate().compareTo(java.math.BigDecimal.ZERO) < 0 || request.getMinimumPassRate().compareTo(java.math.BigDecimal.valueOf(100)) > 0))
            return WebResponse.Error(400, I18nUtils.getMessage("agent.evaluation.policy.minimum-pass-rate.invalid"));
        if (request.getRepeats() != null && (request.getRepeats() < 1 || request.getRepeats() > 5)) return WebResponse.Error(400, I18nUtils.getMessage("agent.evaluation.policy.repeats.invalid"));
        if (request.getCaseTimeoutSeconds() != null && (request.getCaseTimeoutSeconds() < 30 || request.getCaseTimeoutSeconds() > 3600)) return WebResponse.Error(400, I18nUtils.getMessage("agent.evaluation.policy.timeout.invalid"));
        if (request.getParallelism() != null && (request.getParallelism() < 1 || request.getParallelism() > 10)) return WebResponse.Error(400, I18nUtils.getMessage("agent.evaluation.policy.parallelism.invalid"));
        EvaluationPolicy current = service.getOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<EvaluationPolicy>()
                .eq("target_type", request.getTargetType()).eq("target_id", request.getTargetId()).in(dataScopeService != null, "created_by", readableCreatorIds()).eq("deleted", false), false);
        if (current == null) {
            request.setId(null);
            if (request.getRequired() == null) request.setRequired(false);
            if (request.getMinimumScore() == null) request.setMinimumScore(0);
            if (request.getMinimumPassRate() == null) request.setMinimumPassRate(java.math.BigDecimal.valueOf(100));
            if (request.getRepeats() == null) request.setRepeats(1);
            if (request.getCaseTimeoutSeconds() == null) request.setCaseTimeoutSeconds(300);
            if (request.getParallelism() == null) request.setParallelism(2);
            service.save(request);
            return WebResponse.OK(null, request.getId());
        }
        current.setRequired(request.getRequired() == null ? false : request.getRequired());
        current.setMinimumScore(request.getMinimumScore() == null ? 0 : request.getMinimumScore());
        current.setDatasetVersionId(request.getDatasetVersionId()); current.setMinimumPassRate(request.getMinimumPassRate() == null ? java.math.BigDecimal.valueOf(100) : request.getMinimumPassRate());
        current.setRequireReview(Boolean.TRUE.equals(request.getRequireReview())); current.setRepeats(request.getRepeats() == null ? 1 : request.getRepeats());
        current.setCaseTimeoutSeconds(request.getCaseTimeoutSeconds() == null ? 300 : request.getCaseTimeoutSeconds()); current.setParallelism(request.getParallelism() == null ? 2 : request.getParallelism()); current.setRevision((current.getRevision() == null ? 0 : current.getRevision()) + 1);
        service.updateById(current);
        return WebResponse.OK(null, current.getId());
    }

    private java.util.List<String> readableCreatorIds() {
        return dataScopeService == null ? java.util.Collections.emptyList() : dataScopeService.readableCreatorIds(null);
    }

    private void assertTargetReadable(String targetType, String targetId) {
        if (dataScopeService == null) return;
        String owner = null;
        if ("AGENT".equals(targetType) && agentService != null) {
            AgentDefinition target = agentService.getById(targetId);
            if (target == null || Boolean.TRUE.equals(target.getDeleted())) throw new ServerException(404, I18nUtils.getMessage("agent.evaluation.target.not-found"));
            owner = target.getCreatedBy();
        } else if ("WORKFLOW".equals(targetType) && workflowService != null) {
            AgentWorkflow target = workflowService.getById(targetId);
            if (target == null || Boolean.TRUE.equals(target.getDeleted())) throw new ServerException(404, I18nUtils.getMessage("agent.evaluation.target.not-found"));
            owner = target.getCreatedBy();
        } else if ("SKILL".equals(targetType) && skillService != null) {
            AgentSkill target = skillService.getById(targetId);
            if (target == null || Boolean.TRUE.equals(target.getDeleted())) throw new ServerException(404, I18nUtils.getMessage("agent.evaluation.target.not-found"));
            owner = target.getCreatedBy();
        }
        dataScopeService.assertReadable(owner);
    }

    @GetMapping("/check")
    public WebResponse<?> check(@RequestParam @NotBlank String targetType, @RequestParam @NotBlank String targetId, @RequestParam(required=false) String fingerprint) {
        if (gateService == null) return WebResponse.Error(503, I18nUtils.getMessage("agent.evaluation.gate.unavailable"));
        return WebResponse.OK(gateService.check(targetType, targetId, fingerprint));
    }
}
