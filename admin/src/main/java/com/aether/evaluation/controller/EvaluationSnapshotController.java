package com.aether.evaluation.controller;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.entity.WebResponse;
import com.aether.evaluation.entity.EvaluationTargetSnapshot;
import com.aether.evaluation.service.EvaluationSnapshotService;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.permission.Permission;
import com.aether.sys.service.AccountDataScopeService;
import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.service.AgentWorkflowService;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Api(tags = "评测目标快照 API")
@RestController
@RequestMapping("/api/evaluation/snapshots")
@Permission(path = "/evaluation/experiments")
public class EvaluationSnapshotController {
    private final EvaluationSnapshotService service;
    @Autowired(required = false) private AccountDataScopeService dataScopeService;
    @Autowired(required = false) private AgentDefinitionService agentService;
    @Autowired(required = false) private AgentWorkflowService workflowService;

    public EvaluationSnapshotController(EvaluationSnapshotService service) {
        this.service = service;
    }

    @PostMapping
    @Permission(path = "/evaluation/experiments", type = Permission.Type.Write)
    public WebResponse<?> create(@RequestBody EvaluationTargetSnapshot request) {
        if (request == null || blank(request.getTargetType()) || blank(request.getTargetId()))
            return WebResponse.Error(422, I18nUtils.getMessage("agent.evaluation.snapshot.required-fields"));
        if (!"AGENT".equals(request.getTargetType()) && !"WORKFLOW".equals(request.getTargetType()))
            return WebResponse.Error(422, I18nUtils.getMessage("agent.evaluation.target-type.invalid"));
        assertTargetReadable(request.getTargetType(), request.getTargetId());
        try {
            return WebResponse.OK(service.createCurrentSnapshot(request.getTargetType(), request.getTargetId(), CurrentUser.userId()));
        } catch (IllegalArgumentException e) {
            return WebResponse.Error(422, I18nUtils.getMessage("agent.evaluation.snapshot.content.invalid"));
        }
    }

    private void assertTargetReadable(String type, String id) {
        if (dataScopeService == null) return;
        String owner;
        if ("AGENT".equals(type)) {
            AgentDefinition target = agentService == null ? null : agentService.getById(id);
            if (target == null || Boolean.TRUE.equals(target.getDeleted()))
                throw new ServerException(404, I18nUtils.getMessage("agent.evaluation.target.not-found"));
            owner = target.getCreatedBy();
        } else {
            AgentWorkflow target = workflowService == null ? null : workflowService.getById(id);
            if (target == null || Boolean.TRUE.equals(target.getDeleted()))
                throw new ServerException(404, I18nUtils.getMessage("agent.evaluation.target.not-found"));
            owner = target.getCreatedBy();
        }
        dataScopeService.assertReadable(owner);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
