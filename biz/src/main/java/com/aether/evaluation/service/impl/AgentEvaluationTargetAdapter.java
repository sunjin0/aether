package com.aether.evaluation.service.impl;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.DeepAgentRunService;
import com.aether.agent.service.DeepAgentSigningClient;
import com.aether.agent.service.AgentRunService;
import com.aether.agent.service.KnowledgeContextService;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.AgentTool;
import com.aether.i18n.I18nUtils;
import com.aether.evaluation.entity.EvaluationCaseVersion;
import com.aether.evaluation.entity.EvaluationResult;
import com.aether.evaluation.entity.EvaluationTask;
import com.aether.evaluation.service.EvaluationCaseVersionService;
import com.aether.evaluation.service.EvaluationResultService;
import com.aether.evaluation.service.EvaluationTargetAdapter;
import com.aether.evaluation.service.EvaluationSnapshotService;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.UUID;

/** Dispatches an Agent evaluation as an isolated Deep Agent run. */
@Service
public class AgentEvaluationTargetAdapter implements EvaluationTargetAdapter {
    private final AgentDefinitionService agentService; private final EvaluationResultService resultService;
    private final EvaluationCaseVersionService caseService; private final DeepAgentRunService deepRunService; private final EvaluationSnapshotService snapshotService; private final DeepAgentSigningClient signingClient; private final AgentRunService agentRunService; private final StandardAgentEvaluationExecutor standardExecutor; private final com.aether.evaluation.service.EvaluationResultCallbackService callbackService; private final com.aether.evaluation.service.EvaluationExperimentService experimentService; private final KnowledgeContextService knowledgeContextService;
    public AgentEvaluationTargetAdapter(AgentDefinitionService agentService, EvaluationResultService resultService, EvaluationCaseVersionService caseService, DeepAgentRunService deepRunService, EvaluationSnapshotService snapshotService, DeepAgentSigningClient signingClient, AgentRunService agentRunService, StandardAgentEvaluationExecutor standardExecutor, com.aether.evaluation.service.EvaluationResultCallbackService callbackService, com.aether.evaluation.service.EvaluationExperimentService experimentService, KnowledgeContextService knowledgeContextService){this.agentService=agentService;this.resultService=resultService;this.caseService=caseService;this.deepRunService=deepRunService;this.snapshotService=snapshotService;this.signingClient=signingClient;this.agentRunService=agentRunService;this.standardExecutor=standardExecutor;this.callbackService=callbackService;this.experimentService=experimentService;this.knowledgeContextService=knowledgeContextService;}
    @Override public boolean supports(EvaluationTask task){return "EXECUTE".equals(task.getPhase())&&"AGENT".equals(task.getTargetType());}
    @Override public void dispatch(EvaluationTask task){
        EvaluationResult result=resultService.getById(task.getResultId()); if(result==null)throw new IllegalArgumentException(I18nUtils.getMessage("agent.evaluation.result.not-found"));
        EvaluationCaseVersion item=caseService.getById(result.getCaseVersionId()); if(item==null)throw new IllegalArgumentException(I18nUtils.getMessage("agent.evaluation.case-version.not-found"));
        com.aether.evaluation.entity.EvaluationTargetSnapshot targetSnapshot=snapshotService.getById(task.getSnapshotId()); if(targetSnapshot==null)throw new IllegalArgumentException(I18nUtils.getMessage("agent.evaluation.snapshot.not-found"));
        JSONObject snapshot=JSONObject.parseObject(targetSnapshot.getSnapshotJson()); if(snapshot==null)throw new IllegalArgumentException(I18nUtils.getMessage("agent.evaluation.snapshot.content.invalid"));
        AgentDefinition agent=agentService.getById(task.getTargetId()); if(agent==null)throw new IllegalArgumentException(I18nUtils.getMessage("agent.evaluation.target.not-found"));
        if(snapshot.containsKey("systemPrompt")) agent.setSystemPrompt(snapshot.getString("systemPrompt"));
        if(snapshot.containsKey("modelProviderId")) agent.setModelProviderId(snapshot.getString("modelProviderId"));
        if(snapshot.containsKey("modelId")) agent.setModelId(snapshot.getString("modelId"));
        if(snapshot.containsKey("contextCompressionModelId")) agent.setContextCompressionModelId(snapshot.getString("contextCompressionModelId"));
        if(snapshot.containsKey("model")) agent.setModel(snapshot.getString("model"));
        if(snapshot.containsKey("temperature")) agent.setTemperature(snapshot.getBigDecimal("temperature"));
        if(snapshot.containsKey("maxTokens")) agent.setMaxTokens(snapshot.getInteger("maxTokens"));
        if(snapshot.containsKey("maxToolRounds")) agent.setMaxToolRounds(snapshot.getInteger("maxToolRounds"));
        // Old snapshots predate the strategy field; freeze their historical default
        // instead of accidentally reading a newer live Agent configuration.
        agent.setReasoningStrategy(snapshot.containsKey("reasoningStrategy")
                ? snapshot.getString("reasoningStrategy") : "REACT");
        if(snapshot.containsKey("defaultThinking")) agent.setDefaultThinking(snapshot.getBoolean("defaultThinking"));
        if(snapshot.containsKey("defaultReasoningEffort")) agent.setDefaultReasoningEffort(snapshot.getString("defaultReasoningEffort"));
        if(snapshot.containsKey("executionMode")) agent.setExecutionMode(snapshot.getString("executionMode"));
        JSONObject input=JSONObject.parseObject(item.getInputJson()); String message=resolveMessage(input,item.getInputJson());
        if(message==null||message.trim().isEmpty())throw new IllegalArgumentException(I18nUtils.getMessage("agent.evaluation.agent-input.message.required"));
        if("STANDARD".equalsIgnoreCase(agent.getExecutionMode())){try{StandardAgentEvaluationExecutor.CompletedRun completed=standardExecutor.execute(task,result,agent,snapshot,message,input==null?new JSONObject():input.getJSONObject("variables"));result.setRunId(completed.getRunId());resultService.updateById(result);callbackService.complete(completed.getRunId(),completed.getOutputJson(),completed.getEvidenceJson(),completed.getMetricsJson());}catch(StandardAgentEvaluationExecutor.EvaluationExecutionException failure){if(failure.getRunId()!=null){result.setRunId(failure.getRunId());resultService.updateById(result);callbackService.fail(failure.getRunId(),"EVALUATION_STANDARD_EXECUTION_FAILED",I18nUtils.getMessage("agent.evaluation.standard-agent.execution.failed"));}else{throw failure;}}return;}
        java.util.List<AgentTool> tools=com.alibaba.fastjson2.JSON.parseArray(snapshot.getJSONArray("tools") == null ? "[]" : snapshot.getJSONArray("tools").toJSONString(),AgentTool.class);
        Set<String> knowledgeBaseIds=frozenKnowledgeBaseIds(snapshot); List<Map<String,Object>> retrievalSources=knowledgeContextService.enhance(new ArrayList<>(),"evaluation",null,agent.getId(),message,knowledgeBaseIds,"ENABLED");
        String runId=deepRunService.startEvaluationRun(agent,"evaluation",UUID.randomUUID().toString(),message,UUID.randomUUID().toString(),tools,resolveFrozenSystemPrompt(snapshot),knowledgeBaseIds,retrievalSources);
        AgentRun run=agentRunService.getById(runId);if(run!=null){run.setEvaluationResultId(result.getId());run.setEvaluationSnapshotId(task.getSnapshotId());agentRunService.updateById(run);}
        result.setRunId(runId);result.setExecutionStatus("RUNNING");resultService.updateById(result);
    }
    private String resolveFrozenSystemPrompt(JSONObject snapshot){String base=snapshot.getString("systemPrompt");StringBuilder prompt=new StringBuilder(base==null?"":base);com.alibaba.fastjson2.JSONArray skills=snapshot.getJSONArray("skills");if(skills!=null)for(Object item:skills){JSONObject skill=(JSONObject)item;String instruction=skill.getString("instruction");if(instruction!=null&&!instruction.isBlank())prompt.append("\n\n").append(instruction);}return prompt.toString();}
    private Set<String> frozenKnowledgeBaseIds(JSONObject snapshot){Set<String> ids=new LinkedHashSet<>();com.alibaba.fastjson2.JSONArray values=snapshot.getJSONArray("knowledgeBases");if(values==null)return ids;for(Object value:values){if(value instanceof JSONObject item){String id=item.getString("knowledgeBaseId");if(id!=null&&!id.isBlank())ids.add(id);}}return ids;}
    /** Accept the common case-input field names while preserving the raw text form. */
    private String resolveMessage(JSONObject input,String rawInput){
        if(input==null)return rawInput;
        for(String key:new String[]{"message","question","prompt","input"}){
            String value=input.getString(key);if(value!=null&&!value.trim().isEmpty())return value;
        }
        return null;
    }
    @Override public void cancel(String resultId){ EvaluationResult result=resultService.getById(resultId); if(result==null||!"RUNNING".equals(result.getExecutionStatus()))return; com.aether.evaluation.entity.EvaluationExperiment experiment=experimentService.getById(result.getExperimentId());AgentDefinition agent=experiment==null?null:agentService.getById(experiment.getTargetId());if(agent!=null&&"STANDARD".equalsIgnoreCase(agent.getExecutionMode())){standardExecutor.cancel(resultId);return;} if(result.getRunId()!=null&&!result.getRunId().isBlank()){java.util.Map<String,String> body=new java.util.HashMap<>();body.put("run_id",result.getRunId());signingClient.signedPost("/v1/runs/"+result.getRunId()+"/cancel",body);} }
}
