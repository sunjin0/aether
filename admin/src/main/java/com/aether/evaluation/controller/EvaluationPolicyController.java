package com.aether.evaluation.controller;

import com.aether.entity.WebResponse;
import com.aether.evaluation.entity.EvaluationPolicy;
import com.aether.evaluation.service.EvaluationPolicyService;
import com.aether.permission.Permission;
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

    public EvaluationPolicyController(EvaluationPolicyService service) { this.service = service; }

    @ApiOperation("查询评测发布策略")
    @GetMapping
    public WebResponse<EvaluationPolicy> get(@RequestParam @NotBlank String targetType,
                                             @RequestParam @NotBlank String targetId) {
        return WebResponse.OK(service.getOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<EvaluationPolicy>()
                .eq("target_type", targetType).eq("target_id", targetId).eq("deleted", false), false));
    }

    @ApiOperation("保存评测发布策略")
    @PostMapping
    @Permission(path = "/agent/run", type = Permission.Type.Write)
    public WebResponse<String> save(@RequestBody EvaluationPolicy request) {
        if (request == null || StringUtils.isBlank(request.getTargetType()) || StringUtils.isBlank(request.getTargetId()))
            return WebResponse.Error(400, "targetType 和 targetId 不能为空");
        if (!"WORKFLOW".equals(request.getTargetType()) && !"AGENT".equals(request.getTargetType()) && !"SKILL".equals(request.getTargetType()))
            return WebResponse.Error(400, "targetType 必须为 WORKFLOW、AGENT 或 SKILL");
        if (request.getMinimumScore() != null && (request.getMinimumScore() < 0 || request.getMinimumScore() > 100))
            return WebResponse.Error(400, "minimumScore 必须在 0 到 100 之间");
        EvaluationPolicy current = service.getOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<EvaluationPolicy>()
                .eq("target_type", request.getTargetType()).eq("target_id", request.getTargetId()).eq("deleted", false), false);
        if (current == null) {
            request.setId(null);
            if (request.getRequired() == null) request.setRequired(false);
            if (request.getMinimumScore() == null) request.setMinimumScore(0);
            service.save(request);
            return WebResponse.OK(request.getId());
        }
        current.setRequired(request.getRequired() == null ? false : request.getRequired());
        current.setMinimumScore(request.getMinimumScore() == null ? 0 : request.getMinimumScore());
        service.updateById(current);
        return WebResponse.OK(current.getId());
    }
}
