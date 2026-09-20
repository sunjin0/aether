package com.aether.evaluation.service.impl;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.executor.ToolExecutionResult;
import com.aether.agent.model.CancellationToken;
import com.aether.agent.model.ModelChatMessage;
import com.aether.agent.model.ModelChatRequest;
import com.aether.agent.model.ModelChatResponse;
import com.aether.agent.model.ModelClient;
import com.aether.agent.model.ModelClientFactory;
import com.aether.agent.service.AgentRunService;
import com.aether.agent.service.KnowledgeContextService;
import com.aether.agent.service.ModelCatalogService;
import com.aether.agent.tools.AgentToolWorkflow;
import com.aether.evaluation.entity.EvaluationResult;
import com.aether.evaluation.entity.EvaluationTask;
import com.aether.execution.entity.Execution;
import com.aether.execution.service.ExecutionService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes a STANDARD Agent evaluation without creating a business conversation,
 * message, session, or memory record.  Every runtime dependency comes from the
 * immutable target snapshot supplied by the evaluation task.
 */
@Service
public class StandardAgentEvaluationExecutor {
    private static final int MAX_TOOL_ROUNDS = 5;
    private static final int MAX_ALLOWED_TOOL_ROUNDS = 20;
    private final ModelCatalogService modelCatalogService;
    private final ModelClientFactory modelClientFactory;
    private final AgentToolWorkflow agentToolWorkflow;
    private final AgentRunService agentRunService;
    private final ExecutionService executionService;
    private final KnowledgeContextService knowledgeContextService;
    private final ConcurrentHashMap<String, EvaluationCancellationToken> cancellations = new ConcurrentHashMap<>();

    public StandardAgentEvaluationExecutor(ModelCatalogService modelCatalogService,
                                           ModelClientFactory modelClientFactory,
                                           AgentToolWorkflow agentToolWorkflow,
                                           AgentRunService agentRunService,
                                           ExecutionService executionService,
                                           KnowledgeContextService knowledgeContextService) {
        this.modelCatalogService = modelCatalogService;
        this.modelClientFactory = modelClientFactory;
        this.agentToolWorkflow = agentToolWorkflow;
        this.agentRunService = agentRunService;
        this.executionService = executionService;
        this.knowledgeContextService = knowledgeContextService;
    }

    public CompletedRun execute(EvaluationTask task, EvaluationResult result, AgentDefinition frozenAgent,
                                JSONObject snapshot, String message, JSONObject variables) {
        EvaluationCancellationToken token = new EvaluationCancellationToken();
        cancellations.put(result.getId(), token);
        long startedAt = System.currentTimeMillis();
        AgentRun run = null;
        Execution execution = null;
        try {
            token.throwIfCancelled();
            run = createRunningRun(task, result, frozenAgent, null, Collections.emptyList());
            ModelProvider provider = modelCatalogService.resolveProvider(frozenAgent.getModelId(), "CHAT,MULTIMODAL");
            List<AgentTool> tools = frozenTools(snapshot);
            List<ModelChatMessage> messages = new ArrayList<>();
            String prompt = frozenPrompt(snapshot);
            if (StringUtils.isNotBlank(prompt)) messages.add(new ModelChatMessage("system", prompt));
            String input = renderInput(message, variables);
            messages.add(new ModelChatMessage("user", input));
            Set<String> knowledgeBaseIds = frozenKnowledgeBaseIds(snapshot);
            List<Map<String, Object>> retrievalSources = knowledgeContextService.enhanceCancellable(messages,
                    "evaluation", null, frozenAgent.getId(), input, knowledgeBaseIds, "ENABLED", token);

            run.setInputContent(JSON.toJSONString(messages));
            run.setModel(provider.getDefaultModel());
            run.setModelProviderId(provider.getId());
            agentRunService.updateById(run);
            execution = startExecution(task, result, frozenAgent, run);
            ModelChatRequest request = new ModelChatRequest();
            request.setRequestId("evaluation-" + result.getId());
            request.setAgent(frozenAgent);
            request.setProvider(provider);
            request.setMessages(messages);
            request.setTools(isReactEnabled(frozenAgent)
                    ? agentToolWorkflow.getRequestTools(tools, message, Collections.emptySet())
                    : Collections.<AgentTool>emptyList());
            request.setTemperature(frozenAgent.getTemperature());
            request.setMaxCompletionTokens(frozenAgent.getMaxTokens());
            request.setReasoningEffort(frozenAgent.getDefaultReasoningEffort());

            ModelClient client = modelClientFactory.getClient(provider);
            ModelChatResponse response = client.chat(request, token);
            JSONArray toolEvidence = new JSONArray();
            int rounds = 0;
            while (hasToolCalls(response) && rounds++ < maxToolRounds(frozenAgent)) {
                token.throwIfCancelled();
                List<ToolExecutionResult> results = agentToolWorkflow.executeMcpCalls(response, frozenAgent,
                        "evaluation", run.getId(), tools, token);
                recordToolEvidence(response, results, tools, toolEvidence);
                appendToolResults(messages, response, results);
                request.setMessages(messages);
                response = client.chat(request, token);
            }
            if (hasToolCalls(response)) throw new IllegalStateException("EVALUATION_TOOL_ROUND_LIMIT");
            long elapsed = System.currentTimeMillis() - startedAt;
            finishRun(run, response, elapsed, 0, null);
            finishExecution(execution, response, elapsed, "SUCCEEDED", null);
            JSONObject output = new JSONObject();
            output.put("content", response.getContent());
            if (StringUtils.isNotBlank(response.getReasoningContent())) output.put("reasoningContent", response.getReasoningContent());
            JSONObject evidence = new JSONObject();
            evidence.put("runId", run.getId());
            evidence.put("runOrigin", "EVALUATION");
            evidence.put("snapshotId", task.getSnapshotId());
            evidence.put("tools", toolEvidence);
            evidence.put("knowledgeBaseIds", knowledgeBaseIds);
            evidence.put("retrievalSources", retrievalSources);
            JSONObject metrics = new JSONObject();
            metrics.put("latencyMs", elapsed);
            metrics.put("promptTokens", response.getPromptTokens());
            metrics.put("completionTokens", response.getCompletionTokens());
            metrics.put("totalTokens", response.getTotalTokens());
            return new CompletedRun(run.getId(), output.toJSONString(), evidence.toJSONString(), metrics.toJSONString());
        } catch (RuntimeException error) {
            long elapsed = System.currentTimeMillis() - startedAt;
            if (run != null) finishRun(run, null, elapsed, token.isCancelled() ? 5 : 1, error.getMessage());
            if (execution != null) finishExecution(execution, null, elapsed, token.isCancelled() ? "CANCELLED" : "FAILED", error.getMessage());
            throw new EvaluationExecutionException(run == null ? null : run.getId(), error);
        } finally {
            cancellations.remove(result.getId(), token);
        }
    }

    private boolean isReactEnabled(AgentDefinition agent) {
        return agent == null || !"DIRECT".equalsIgnoreCase(agent.getReasoningStrategy());
    }

    private int maxToolRounds(AgentDefinition agent) {
        if (!isReactEnabled(agent)) return 0;
        Integer configured = agent == null ? null : agent.getMaxToolRounds();
        return configured == null || configured <= 0 ? MAX_TOOL_ROUNDS : Math.min(configured, MAX_ALLOWED_TOOL_ROUNDS);
    }

    public void cancel(String resultId) {
        EvaluationCancellationToken token = cancellations.get(resultId);
        if (token != null) token.cancel();
    }

    private AgentRun createRunningRun(EvaluationTask task, EvaluationResult result, AgentDefinition agent,
                                      ModelProvider provider, List<ModelChatMessage> messages) {
        AgentRun run = new AgentRun();
        run.setApplicationId(agent.getApplicationId());
        run.setAgentDefinitionId(agent.getId());
        run.setUserId("evaluation");
        run.setInputContent(JSON.toJSONString(messages));
        run.setModel(provider == null ? agent.getModel() : provider.getDefaultModel());
        run.setModelProviderId(provider == null ? agent.getModelProviderId() : provider.getId());
        run.setStatus(4);
        run.setExecutionMode("STANDARD");
        run.setRequestId("evaluation-" + result.getId());
        run.setRunOrigin("EVALUATION");
        run.setEvaluationResultId(result.getId());
        run.setEvaluationSnapshotId(task.getSnapshotId());
        agentRunService.save(run);
        return run;
    }

    private Execution startExecution(EvaluationTask task, EvaluationResult result, AgentDefinition agent, AgentRun run) {
        Execution execution = executionService.start("AGENT", null, null, "evaluation", agent.getId());
        execution.setRunOrigin("EVALUATION");
        execution.setEvaluationResultId(result.getId());
        execution.setEvaluationSnapshotId(task.getSnapshotId());
        execution.setApplicationId(agent.getApplicationId());
        execution.setStartedAt(System.currentTimeMillis());
        executionService.updateById(execution);
        run.setExecutionId(execution.getId());
        agentRunService.updateById(run);
        return execution;
    }

    private void finishRun(AgentRun run, ModelChatResponse response, long elapsed, int status, String error) {
        AgentRun update = new AgentRun();
        update.setId(run.getId());
        update.setStatus(status);
        update.setLatencyMs((int) elapsed);
        update.setErrorMsg(error);
        if (response != null) {
            update.setOutputContent(response.getContent());
            update.setRawResponse(response.getRawResponse());
            update.setModel(response.getModel());
            update.setPromptTokens(response.getPromptTokens());
            update.setCompletionTokens(response.getCompletionTokens());
            update.setTotalTokens(response.getTotalTokens());
        }
        agentRunService.updateById(update);
    }

    private void finishExecution(Execution execution, ModelChatResponse response, long elapsed, String status, String error) {
        Execution update = new Execution();
        update.setId(execution.getId());
        update.setPromptTokens(response == null ? null : response.getPromptTokens());
        update.setCompletionTokens(response == null ? null : response.getCompletionTokens());
        update.setTotalTokens(response == null ? null : response.getTotalTokens());
        update.setModel(response == null ? null : response.getModel());
        executionService.updateById(update);
        executionService.finish(execution.getId(), status, error == null ? null : "EVALUATION_STANDARD_EXECUTION_FAILED", error);
    }

    private List<AgentTool> frozenTools(JSONObject snapshot) {
        JSONArray array = snapshot.getJSONArray("tools");
        return array == null ? Collections.emptyList() : JSON.parseArray(array.toJSONString(), AgentTool.class);
    }

    /** Uses only the knowledge bases captured in the immutable target snapshot. */
    private Set<String> frozenKnowledgeBaseIds(JSONObject snapshot) {
        Set<String> ids = new LinkedHashSet<>();
        JSONArray knowledgeBases = snapshot.getJSONArray("knowledgeBases");
        if (knowledgeBases == null) return ids;
        for (Object item : knowledgeBases) {
            if (item instanceof JSONObject value && StringUtils.isNotBlank(value.getString("knowledgeBaseId"))) {
                ids.add(value.getString("knowledgeBaseId"));
            }
        }
        return ids;
    }

    private String frozenPrompt(JSONObject snapshot) {
        StringBuilder prompt = new StringBuilder(StringUtils.defaultString(snapshot.getString("systemPrompt")));
        JSONArray skills = snapshot.getJSONArray("skills");
        if (skills != null) for (Object item : skills) {
            JSONObject skill = (JSONObject) item;
            String instruction = skill.getString("instruction");
            if (StringUtils.isNotBlank(instruction)) prompt.append("\n\n").append(instruction);
        }
        return prompt.toString();
    }

    private String renderInput(String message, JSONObject variables) {
        if (variables == null || variables.isEmpty()) return message;
        return message + "\n\n[variables]\n" + variables.toJSONString();
    }

    private boolean hasToolCalls(ModelChatResponse response) {
        return response != null && StringUtils.isNotBlank(response.getToolCalls()) && !"[]".equals(response.getToolCalls().trim());
    }

    private void appendToolResults(List<ModelChatMessage> messages, ModelChatResponse response,
                                   List<ToolExecutionResult> results) {
        messages.add(new ModelChatMessage("assistant", response.getContent(), response.getToolCalls(), null,
                response.getReasoningContent()));
        for (ToolExecutionResult result : results) {
            String content = result.isSuccess() ? StringUtils.defaultString(result.getContent())
                    : "Tool execution failed: " + StringUtils.defaultString(result.getErrorMsg());
            messages.add(new ModelChatMessage("tool", content, null, result.getToolCallId()));
        }
    }

    /** Stores only stable tool identifiers and outcomes for evaluation assertions. */
    private void recordToolEvidence(ModelChatResponse response, List<ToolExecutionResult> results,
                                    List<AgentTool> tools, JSONArray evidence) {
        try {
            JSONArray calls = JSONArray.parseArray(response.getToolCalls());
            if (calls == null) return;
            for (int index = 0; index < calls.size(); index++) {
                JSONObject call = calls.getJSONObject(index);
                JSONObject function = call == null ? null : call.getJSONObject("function");
                String wireName = function == null ? call == null ? null : call.getString("name") : function.getString("name");
                AgentTool matched = null;
                for (AgentTool tool : tools) {
                    if (wireName != null && (wireName.equals(tool.getCode()) || wireName.equals(tool.getMcpToolName()) || wireName.equals(tool.getName()))) {
                        matched = tool;
                        break;
                    }
                }
                JSONObject item = new JSONObject();
                item.put("toolId", matched == null ? null : matched.getId());
                item.put("wireName", wireName);
                ToolExecutionResult outcome = index < results.size() ? results.get(index) : null;
                item.put("status", outcome != null && outcome.isSuccess() ? "SUCCEEDED" : "FAILED");
                evidence.add(item);
            }
        } catch (RuntimeException ignored) {
            // Tool evidence is supplementary; malformed provider metadata must not expose raw payloads.
        }
    }

    public static final class CompletedRun {
        private final String runId, outputJson, evidenceJson, metricsJson;
        CompletedRun(String runId, String outputJson, String evidenceJson, String metricsJson) {
            this.runId = runId; this.outputJson = outputJson; this.evidenceJson = evidenceJson; this.metricsJson = metricsJson;
        }
        public String getRunId() { return runId; }
        public String getOutputJson() { return outputJson; }
        public String getEvidenceJson() { return evidenceJson; }
        public String getMetricsJson() { return metricsJson; }
    }

    /** Carries the durable run identifier so callers can close the matching evaluation result on failure. */
    public static final class EvaluationExecutionException extends RuntimeException {
        private final String runId;
        EvaluationExecutionException(String runId, RuntimeException cause) { super(cause); this.runId = runId; }
        public String getRunId() { return runId; }
    }

    private static final class EvaluationCancellationToken implements CancellationToken {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        public boolean isCancelled() { return cancelled.get() || Thread.currentThread().isInterrupted(); }
        void cancel() { cancelled.set(true); }
    }
}
