package com.aether.evaluation.service.impl;

import com.aether.evaluation.entity.*;
import com.aether.evaluation.service.*;
import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.runtime.WorkflowDefinitionValidator;
import com.aether.workflow.service.AgentWorkflowExecutionService;
import com.alibaba.fastjson2.JSONObject;
import com.aether.i18n.I18nUtils;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.LinkedHashMap;

/** Dispatches workflow evaluation cases with isolated variables and user identity. */
@Service
public class WorkflowEvaluationTargetAdapter implements EvaluationTargetAdapter {
    private final AgentWorkflowExecutionService executionService; private final EvaluationResultService resultService; private final EvaluationCaseVersionService caseService; private final EvaluationSnapshotService snapshotService;
    public WorkflowEvaluationTargetAdapter(AgentWorkflowExecutionService executionService, EvaluationResultService resultService, EvaluationCaseVersionService caseService, EvaluationSnapshotService snapshotService){this.executionService=executionService;this.resultService=resultService;this.caseService=caseService;this.snapshotService=snapshotService;}
    @Override public boolean supports(EvaluationTask task){return "EXECUTE".equals(task.getPhase())&&"WORKFLOW".equals(task.getTargetType());}
    @Override public void dispatch(EvaluationTask task){EvaluationResult result=resultService.getById(task.getResultId());if(result==null)throw new IllegalArgumentException(I18nUtils.getMessage("agent.evaluation.result.not-found"));EvaluationCaseVersion item=caseService.getById(result.getCaseVersionId());if(item==null)throw new IllegalArgumentException(I18nUtils.getMessage("agent.evaluation.case-version.not-found"));JSONObject input=JSONObject.parseObject(item.getInputJson());Map<String,Object> vars=input==null?new LinkedHashMap<>():new LinkedHashMap<>(input);validateInput(task.getSnapshotId(),vars);AgentWorkflowInstance instance=executionService.startEvaluation(task.getSnapshotId(),vars,"evaluation",result.getId());result.setWorkflowInstanceId(instance.getId());result.setExecutionStatus("RUNNING");resultService.updateById(result);}
    private void validateInput(String snapshotId, Map<String,Object> variables){try{EvaluationTargetSnapshot snapshot=snapshotService.getById(snapshotId);if(snapshot==null||!"WORKFLOW".equals(snapshot.getTargetType()))throw new IllegalArgumentException(I18nUtils.getMessage("agent.evaluation.workflow.snapshot.not-found"));JSONObject definition=JSONObject.parseObject(snapshot.getSnapshotJson());if(definition==null)throw new IllegalArgumentException(I18nUtils.getMessage("agent.evaluation.workflow.snapshot.invalid"));WorkflowDefinitionValidator.validateStartVariables(definition.getString("inputSchema"),variables);}catch(RuntimeException ex){throw new EvaluationPreDispatchException("WORKFLOW_INPUT_INVALID",ex);}}
    @Override public void cancel(String resultId){EvaluationResult result=resultService.getById(resultId);if(result==null||!"RUNNING".equals(result.getExecutionStatus()))return;if(result.getWorkflowInstanceId()!=null&&!result.getWorkflowInstanceId().isBlank())executionService.terminate(result.getWorkflowInstanceId(),"evaluation");}
}
