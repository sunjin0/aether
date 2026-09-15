package com.aether.agent.executor.impl;

import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.executor.ToolExecutionContext;
import com.aether.agent.executor.ToolExecutionResult;
import com.aether.agent.service.AgentRunService;
import com.aether.i18n.I18nUtils;
import com.aether.workflow.service.AgentWorkflowInvocationService;
import com.aether.workflow.service.AgentWorkflowCapabilityService;
import com.aether.workflow.entity.AgentWorkflowCapability;
import com.aether.workflow.vo.AgentWorkflowInvocationObservation;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** 执行 Agent 绑定的工作流能力工具。 */
@Component
public class WorkflowToolExecutor implements com.aether.agent.executor.ToolExecutor {
    private final AgentWorkflowInvocationService invocationService;
    private final AgentRunService agentRunService;
    private final AgentWorkflowCapabilityService capabilityService;

    @Autowired
    public WorkflowToolExecutor(@Lazy AgentWorkflowInvocationService invocationService, AgentRunService agentRunService,
                                @Lazy AgentWorkflowCapabilityService capabilityService) {
        this.invocationService = invocationService;
        this.agentRunService = agentRunService;
        this.capabilityService = capabilityService;
    }

    /** Compatibility constructor for direct unit tests and legacy embedders. */
    public WorkflowToolExecutor(@Lazy AgentWorkflowInvocationService invocationService, AgentRunService agentRunService) {
        this(invocationService, agentRunService, null);
    }

    @Override
    public boolean supports(String toolType) {
        return "workflow".equalsIgnoreCase(StringUtils.defaultString(toolType));
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionContext context) {
        AgentTool tool = context == null ? null : context.getTool();
        if (tool == null || !supports(tool.getToolType()) && !supports(tool.getType()))
            return ToolExecutionResult.failure(I18nUtils.getMessage("agent.workflow.tool.capability.not-bound"), 3);
        try {
            Map<String, Object> arguments = new LinkedHashMap<>(context.getArguments() == null
                    ? java.util.Collections.<String, Object>emptyMap() : context.getArguments());
            Map<String, Object> requestArguments = new LinkedHashMap<>(arguments);
            String action = StringUtils.defaultIfBlank(tool.getWorkflowToolAction(), "START");
            String capabilityId = tool.getWorkflowCapabilityId();
            AgentRun run = StringUtils.isBlank(context.getRunId()) ? null : agentRunService.getById(context.getRunId());
            String taskId = run == null ? null : run.getTaskId();
            long started = System.currentTimeMillis();
            if ("START".equalsIgnoreCase(action)) {
                capabilityId = resolveStartCapabilityId(context, arguments, capabilityId);
                String operationKey = string(arguments.remove("operationKey"));
                if (StringUtils.isBlank(operationKey)) operationKey = string(arguments.remove("_operationKey"));
                if (StringUtils.isBlank(operationKey)) operationKey = context.getIdempotencyKey();
                if (StringUtils.isBlank(operationKey)) operationKey = StringUtils.defaultIfBlank(context.getRunId(), "anonymous") + ":" + capabilityId;
                Object inputValue = arguments.remove("input");
                if (inputValue instanceof Map) {
                    arguments = new LinkedHashMap<>((Map<String, Object>) inputValue);
                } else {
                    arguments.remove("capabilityCode");
                }
                AgentWorkflowInvocationService.AgentWorkflowInvocationResult result = invocationService.start(
                        capabilityId, operationKey, arguments, context.getAgentDefinitionId(),
                        context.getRunId(), taskId, context.getIdempotencyKey(), context.getUserId(), context.getApplicationId());
                return success(JSON.toJSONString(result), started, requestArguments);
            }
            String invocationId = string(arguments.get("invocationId"));
            if (StringUtils.isBlank(invocationId)) {
                return structuredFailure(new IllegalArgumentException("Workflow invocationId is required"),
                        requestedAction(arguments, tool), null, longValue(arguments.get("expectedStateVersion")), context, arguments);
            }
            // 动作专用工具不再要求模型重复传 action；保留 INSTANCE 的 action
            // 读取逻辑仅用于历史调用和非模型内部调用。
            String requested = StringUtils.defaultIfBlank(string(arguments.get("action")),
                    StringUtils.defaultIfBlank(tool.getWorkflowToolAction(), "OBSERVE")).toUpperCase();
            Long expectedStateVersion = longValue(arguments.get("expectedStateVersion"));
            if ("STOP".equals(requested)) {
                AgentWorkflowInvocationService.AgentWorkflowInvocationResult result = invocationService.stop(
                        invocationId, string(arguments.get("reason")), expectedStateVersion,
                        context.getUserId(), context.getAgentDefinitionId());
                return success(JSON.toJSONString(result), started, requestArguments);
            }
            if ("PROVIDE_AGENT_INPUT".equals(requested)) {
                Object value = arguments.get("input");
                Map<String, Object> input = value instanceof Map
                        ? new LinkedHashMap<>((Map<String, Object>) value) : new LinkedHashMap<>();
                AgentWorkflowInvocationService.AgentWorkflowInvocationResult result = invocationService.provideAgentInput(
                        invocationId, input, expectedStateVersion, context.getUserId(), context.getAgentDefinitionId());
                return success(JSON.toJSONString(result), started, requestArguments);
            }
            if ("RESOLVE_MCP_APPROVAL".equals(requested)) {
                AgentWorkflowInvocationService.AgentWorkflowInvocationResult result = invocationService.resolveMcpApproval(
                        invocationId, string(arguments.get("decision")), expectedStateVersion,
                        context.getUserId(), context.getAgentDefinitionId());
                return success(JSON.toJSONString(result), started, requestArguments);
            }
            if ("SIGNAL_EVENT".equals(requested)) {
                Object value = arguments.get("data");
                Map<String, Object> data = value instanceof Map
                        ? new LinkedHashMap<>((Map<String, Object>) value) : new LinkedHashMap<>();
                AgentWorkflowInvocationService.AgentWorkflowInvocationResult result = invocationService.signalEvent(
                        invocationId, string(arguments.get("eventType")), string(arguments.get("eventId")),
                        string(arguments.get("correlationKey")), data, expectedStateVersion,
                        context.getUserId(), context.getAgentDefinitionId());
                return success(JSON.toJSONString(result), started, requestArguments);
            }
            if ("RETRY_NODE".equals(requested)) {
                AgentWorkflowInvocationService.AgentWorkflowInvocationResult result = invocationService.retryNode(
                        invocationId, string(arguments.get("nodeId")), expectedStateVersion,
                        context.getUserId(), context.getAgentDefinitionId());
                return success(JSON.toJSONString(result), started, requestArguments);
            }
            if (!"OBSERVE".equals(requested)) {
                return structuredFailure(new IllegalArgumentException("Unsupported workflow invocation action: " + requested),
                        requested, invocationId, expectedStateVersion, context, arguments);
            }
            AgentWorkflowInvocationObservation result = invocationService.observe(invocationId, context.getUserId(), context.getAgentDefinitionId());
            return success(JSON.toJSONString(result), started, requestArguments);
        } catch (Exception e) {
            Map<String, Object> failedArguments = context == null || context.getArguments() == null
                    ? java.util.Collections.<String, Object>emptyMap() : context.getArguments();
            return structuredFailure(e, requestedAction(failedArguments, tool),
                    string(failedArguments.get("invocationId")), longValue(failedArguments.get("expectedStateVersion")), context, failedArguments);
        }
    }

    private String resolveStartCapabilityId(ToolExecutionContext context, Map<String, Object> arguments,
                                            String legacyCapabilityId) {
        if (StringUtils.isNotBlank(legacyCapabilityId)) return legacyCapabilityId;
        String capabilityCode = string(arguments.get("capabilityCode"));
        if (StringUtils.isBlank(capabilityCode)) {
            throw new IllegalArgumentException("Workflow capabilityCode is required");
        }
        if (capabilityService == null) {
            throw new IllegalArgumentException("Workflow capability is not bound to agent: " + capabilityCode);
        }
        java.util.List<AgentWorkflowCapability> capabilities = capabilityService.listEnabledForAgent(
                context.getAgentDefinitionId(), context.getApplicationId());
        if (capabilities != null) {
            for (AgentWorkflowCapability capability : capabilities) {
                if (StringUtils.equals(capabilityCode, capability.getCapabilityCode())
                        && hasAction(capability.getAllowedActions(), "START")) {
                    return capability.getId();
                }
            }
        }
        throw new IllegalArgumentException("Workflow capability is not bound to agent: " + capabilityCode);
    }

    private boolean hasAction(String serialized, String action) {
        try {
            return serialized != null && com.alibaba.fastjson2.JSONArray.parseArray(serialized).contains(action);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String requestedAction(Map<String, Object> arguments, AgentTool tool) {
        return StringUtils.defaultIfBlank(string(arguments == null ? null : arguments.get("action")),
                StringUtils.defaultIfBlank(tool == null ? null : tool.getWorkflowToolAction(), "OBSERVE"))
                .toUpperCase();
    }

    /** 将领域异常转换为模型可执行的结构化结果，避免模型从英文错误文本猜下一步。 */
    private ToolExecutionResult structuredFailure(Exception exception, String action,
                                                  String invocationId, Long expectedStateVersion,
                                                  ToolExecutionContext context, Map<String, Object> requestArguments) {
        String message = exception == null ? null : exception.getMessage();
        if (StringUtils.isBlank(message)) message = I18nUtils.getMessage("agent.workflow.tool.execution.failed");
        int httpStatus = 500;
        String code = "WORKFLOW_ACTION_FAILED";
        boolean retryable = false;
        String requiredAction = null;
        if (message.matches("^\\d{3}:.*")) {
            try { httpStatus = Integer.parseInt(message.substring(0, 3)); }
            catch (NumberFormatException ignored) { }
        }
        if (message.contains("State version is required")) {
            code = "WORKFLOW_STATE_VERSION_REQUIRED";
            requiredAction = "OBSERVE";
        } else if (message.contains("Workflow state changed")) {
            code = "WORKFLOW_STATE_CHANGED";
            retryable = true;
            requiredAction = "OBSERVE";
        } else if (message.contains("external result is unknown")) {
            code = "WORKFLOW_EXTERNAL_RESULT_UNKNOWN";
            requiredAction = "WAIT_HUMAN";
        } else if (message.contains("Agent input intervention is not enabled")) {
            code = "WORKFLOW_INPUT_NOT_ENABLED";
            requiredAction = "OBSERVE";
        } else if (message.contains("current published version")) {
            code = "WORKFLOW_CAPABILITY_VERSION_INVALID";
        } else if (message.contains("capabilityCode is required")) {
            code = "WORKFLOW_CAPABILITY_CODE_REQUIRED";
        } else if (message.contains("capability is not bound to agent")) {
            code = "WORKFLOW_CAPABILITY_NOT_BOUND";
        } else if (message.contains("Unsupported workflow invocation action")) {
            code = "WORKFLOW_ACTION_UNSUPPORTED";
        }
        JSONObject payload = new JSONObject();
        payload.put("success", false);
        payload.put("errorCode", code);
        payload.put("message", message);
        payload.put("action", action);
        payload.put("invocationId", invocationId);
        payload.put("expectedStateVersion", expectedStateVersion);
        payload.put("retryable", retryable);
        if (requiredAction != null) payload.put("requiredAction", requiredAction);
        if ("WORKFLOW_STATE_CHANGED".equals(code) && StringUtils.isNotBlank(invocationId)) {
            // 状态冲突时主动读取一次最新快照，给模型一个可执行的“重新观察”闭环。
            try {
                AgentWorkflowInvocationObservation latest = invocationService.observe(invocationId,
                        context == null ? null : context.getUserId(),
                        context == null ? null : context.getAgentDefinitionId());
                if (latest != null) {
                    payload.put("currentState", latest.getStatus());
                    payload.put("latestStateVersion", latest.getStateVersion());
                    payload.put("currentNodeId", latest.getCurrentNodeId());
                    payload.put("nextAction", latest.getNextAction());
                }
            } catch (Exception ignored) {
                // 原始状态冲突仍然可返回；不能因补偿观察失败覆盖根因。
            }
        }
        ToolExecutionResult result = ToolExecutionResult.failure(message, 1);
        result.setContent(payload.toJSONString());
        result.setRawResponse(payload.toJSONString());
        result.setHttpStatus(httpStatus);
        result.setRequestMethod("WORKFLOW");
        result.setRequestBody(JSON.toJSONString(requestArguments == null
                ? java.util.Collections.<String, Object>emptyMap() : requestArguments));
        return result;
    }

    private ToolExecutionResult success(String content, long startedAt, Map<String, Object> arguments) {
        ToolExecutionResult result = ToolExecutionResult.success(content, content, 200,
                (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startedAt));
        result.setRequestMethod("WORKFLOW");
        result.setRequestBody(JSON.toJSONString(arguments == null
                ? java.util.Collections.<String, Object>emptyMap() : arguments));
        return result;
    }


    private String string(Object value) { return value == null ? null : String.valueOf(value); }

    private Long longValue(Object value) {
        if (value == null) return null;
        try { return Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }
}
