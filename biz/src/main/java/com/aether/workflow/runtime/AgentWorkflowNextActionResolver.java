package com.aether.workflow.runtime;

import com.aether.workflow.entity.AgentWorkflowCapability;
import com.aether.workflow.entity.AgentWorkflowNodeInstance;
import com.aether.workflow.service.AgentWorkflowExternalInvocationService;
import com.aether.workflow.vo.AgentWorkflowInstanceVo;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把工作流实例的当前状态翻译成 agent 可执行的下一步动作。
 *
 * <p>等待类型（等待用户输入 / 等待 MCP 授权 / 等待人工）无法从 {@code agent_workflow_invocation.status}
 * 读出来 —— 那是任意 {@code WAITING_*} 的粗粒度投影，只能由当前节点的交互配置与能力的
 * {@code allowedActions} 共同判定。observe 与任务清单两条读路径共用这一份实现，
 * 任何一处再实现一遍都会与这里漂移。
 */
@Component
public class AgentWorkflowNextActionResolver {
    private static final String ACTION_OBSERVE = "OBSERVE";
    private static final String STATUS_WAITING_USER = "WAITING_USER";
    private static final String STATUS_WAITING_EVENT = "WAITING_EVENT";
    private static final String STATUS_FAILED = "FAILED";

    private final AgentWorkflowExternalInvocationService externalInvocationService;

    public AgentWorkflowNextActionResolver(AgentWorkflowExternalInvocationService externalInvocationService) {
        this.externalInvocationService = externalInvocationService;
    }

    /**
     * @param capability 调用所使用的能力。能力可能已被删除，此时按「未授予任何动作」处理，
     *                   而不是把调用判为失败 —— 已启动的工作流仍需能被观察。
     * @param snapshot   工作流实例快照；status 必须是实例真值，不能是 invocation 的投影值。
     * @return 下一步动作，形状与 {@code AgentWorkflowInvocationObservation.nextAction} 一致。
     */
    public Map<String, Object> resolve(AgentWorkflowCapability capability, AgentWorkflowInstanceVo snapshot) {
        Map<String, Object> action = new LinkedHashMap<>();
        if (snapshot == null) return action;
        String status = snapshot.getStatus();
        AgentWorkflowNodeInstance node = currentNode(snapshot);
        JSONObject definition = node == null ? null : nodeDefinition(snapshot, node.getNodeId());
        if (STATUS_WAITING_USER.equals(status) && hasAction(capability, "RESOLVE_MCP_APPROVAL")
                && isMcpToolApprovalConfig(node == null || StringUtils.isBlank(node.getInteractionConfig())
                ? null : JSONObject.parseObject(node.getInteractionConfig()))) {
            action.put("type", "RESOLVE_MCP_APPROVAL");
            action.put("required", Arrays.asList("invocationId", "decision"));
            action.put("authorizationRequired", true);
            action.put("decisions", Arrays.asList("once", "allow_10m", "reject"));
            JSONObject config = JSONObject.parseObject(node.getInteractionConfig());
            if (node != null && StringUtils.isNotBlank(node.getNodeId())) action.put("nodeId", node.getNodeId());
            if (StringUtils.isNotBlank(config.getString("toolName"))) action.put("toolName", config.getString("toolName"));
            if (StringUtils.isNotBlank(config.getString("question"))) action.put("question", config.getString("question"));
        } else if (STATUS_WAITING_USER.equals(status) && hasAction(capability, "PROVIDE_AGENT_INPUT")
                && agentInputAllowed(definition)) {
            action.put("type", "PROVIDE_AGENT_INPUT");
            action.put("required", Arrays.asList("invocationId", "input"));
            if (node != null && StringUtils.isNotBlank(node.getNodeId())) action.put("nodeId", node.getNodeId());
            action.put("schema", agentInputSchema(definition));
        } else if (STATUS_WAITING_USER.equals(status)) action.put("type", "WAITING_HUMAN");
        else if (STATUS_WAITING_EVENT.equals(status) && hasAction(capability, "SIGNAL_EVENT")) {
            action.put("type", "SIGNAL_EVENT");
            action.put("required", Arrays.asList("invocationId", "eventType", "eventId"));
            if (node != null && StringUtils.isNotBlank(node.getInteractionConfig())) {
                JSONObject config = JSONObject.parseObject(node.getInteractionConfig());
                action.put("eventType", config.getString("eventType"));
                action.put("correlationKey", config.getString("correlationKey"));
            }
        } else if (STATUS_FAILED.equals(status) && hasAction(capability, "RETRY_NODE")) {
            if (hasUnknownExternalResult(snapshot.getId())) {
                action.put("type", "WAIT_HUMAN");
                action.put("reason", "EXTERNAL_RESULT_UNKNOWN");
                action.put("retryable", false);
            } else {
                action.put("type", "RETRY_NODE");
                action.put("required", Arrays.asList("invocationId", "nodeId"));
                action.put("nodeId", snapshot.getCurrentNodeId());
            }
        } else if (isTerminal(status)) action.put("type", "NONE");
        else {
            action.put("type", ACTION_OBSERVE);
            action.put("required", Collections.singletonList("invocationId"));
            String note = runningToolNote(node, definition);
            if (note != null) action.put("note", note);
        }
        return action;
    }

    /** 实例是否已走到不会再自推进的终态。 */
    public boolean isTerminal(String status) {
        return "COMPLETED".equals(status) || STATUS_FAILED.equals(status)
                || "TERMINATED".equals(status) || "TIMED_OUT".equals(status);
    }

    /**
     * 终态（及等待人工）状态对应的结果码，取值与 {@code workflow_observe} 一致。
     *
     * <p>放在这里而不是各读路径各写一份：任务清单与 observe 会对同一个实例展示结果码，
     * 两份实现一旦漂移，同一个调用就会出现两个结论。
     */
    public static String resultCode(String status) {
        if ("COMPLETED".equals(status)) return "WORKFLOW_COMPLETED";
        if (STATUS_FAILED.equals(status)) return "WORKFLOW_FAILED";
        if ("TERMINATED".equals(status)) return "WORKFLOW_TERMINATED";
        if ("TIMED_OUT".equals(status)) return "WORKFLOW_TIMED_OUT";
        if (STATUS_WAITING_USER.equals(status)) return "WORKFLOW_WAITING_HUMAN";
        return "WORKFLOW_RUNNING";
    }

    /**
     * 当前节点正由工作流自己执行一个 MCP 工具时，给模型的提示。
     *
     * <p>模型在对话里直接调同一个工具，走的是会话级的另一套审批门，会弹出一张与工作流
     * 无关的确认卡；而且那条路径没有工作流侧的幂等包裹，是真的重复执行。所以在此点名工具，
     * 让模型不必撞上运行时的硬拦截才知道。
     */
    private String runningToolNote(AgentWorkflowNodeInstance node, JSONObject definition) {
        if (node == null || definition == null) return null;
        if (!"tool".equalsIgnoreCase(node.getNodeType())) return null;
        String toolName = StringUtils.defaultIfBlank(definition.getString("toolName"), "该工具");
        return "当前节点正由工作流自行调用工具 " + toolName + "，请勿在对话中直接调用它；"
                + "用 workflow_observe 等待它结束；若它停在授权上，用 workflow_resolve_mcp_approval 处理。";
    }

    /** 交互配置是否是一次待授权的 MCP 工具调用。 */
    public boolean isMcpToolApprovalConfig(JSONObject config) {
        return config != null && ("mcp_tool_approval".equals(config.getString("approvalType"))
                || "mcp_tool_approval".equals(config.getString("type")));
    }

    /**
     * 节点是否允许 agent 代填。审批节点永远不允许 —— 那是人的判断，不是补全。
     *
     * <p>它既是「要不要提示 agent 补填」的依据，也是「接不接受 agent 提交的补填」的把关，
     * 两处必须同源，否则会出现提示了却不接受、或没提示却接受的错位。
     */
    public boolean agentInputAllowed(JSONObject definition) {
        if (definition == null || "approval".equals(definition.getString("mode"))) return false;
        if (definition.getBooleanValue("agentInputAllowed")) return true;
        String policy = StringUtils.defaultIfBlank(definition.getString("agentInputPolicy"),
                definition.getString("inputPolicy"));
        return "AGENT_INPUT_ALLOWED".equalsIgnoreCase(policy);
    }

    private boolean hasAction(AgentWorkflowCapability capability, String action) {
        return capability != null && jsonArrayContains(capability.getAllowedActions(), action);
    }

    private boolean jsonArrayContains(String value, String expected) {
        try { return StringUtils.isNotBlank(value) && JSONArray.parseArray(value).contains(expected); }
        catch (Exception ignored) { return false; }
    }

    /**
     * 从已加载的实例快照里取出当前节点，不额外查库。
     *
     * <p>公开是因为 observe 要用同一个节点回填裸字段；注意它接受的是快照，
     * 与按实例主键查库的同名重载不是一回事。
     */
    public AgentWorkflowNodeInstance currentNode(AgentWorkflowInstanceVo snapshot) {
        if (snapshot.getNodes() == null || StringUtils.isBlank(snapshot.getCurrentNodeId())) return null;
        for (AgentWorkflowNodeInstance node : snapshot.getNodes())
            if (snapshot.getCurrentNodeId().equals(node.getNodeId())) return node;
        return null;
    }

    /** 节点定义里的展示名，取不到时返回 null。 */
    public String nodeName(AgentWorkflowInstanceVo snapshot, String nodeId) {
        JSONObject definition = nodeDefinition(snapshot, nodeId);
        return definition == null ? null : definition.getString("name");
    }

    /**
     * 从已加载的实例快照里取出节点定义（含 {@code resourceId} / {@code toolName}），不额外查库。
     *
     * <p>公开是因为工具占用检测也要按节点定义里的 {@code resourceId} 匹配；两处各解析一遍
     * 必然漂移。
     */
    public JSONObject nodeDefinition(AgentWorkflowInstanceVo snapshot, String nodeId) {
        if (snapshot == null || StringUtils.isBlank(snapshot.getVersionNodes())) return null;
        try {
            for (Object item : JSONArray.parseArray(snapshot.getVersionNodes())) {
                JSONObject value = item instanceof JSONObject ? (JSONObject) item : null;
                if (value != null && StringUtils.equals(nodeId, value.getString("id"))) return value;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private Map<String, Object> agentInputSchema(JSONObject definition) {
        Map<String, Object> schema = new LinkedHashMap<>();
        if (definition == null) return schema;
        Object raw = definition.get("agentInputSchema");
        if (raw == null) raw = definition.get("inputSchema");
        if (raw instanceof JSONObject) {
            JSONObject object = (JSONObject) raw;
            schema.putAll(object);
        } else if (raw != null) {
            try { schema.put("fields", JSONArray.parseArray(String.valueOf(raw))); }
            catch (Exception ignored) { }
        }
        if (schema.isEmpty() && definition.getJSONArray("questions") != null)
            schema.put("fields", definition.getJSONArray("questions"));
        return schema;
    }

    /**
     * 早期节点已把外部副作用提交出去但结果未知时，重试会重复副作用，必须交给人判断。
     */
    private boolean hasUnknownExternalResult(String instanceId) {
        if (externalInvocationService == null || StringUtils.isBlank(instanceId)) return false;
        try {
            return externalInvocationService.listByInstanceId(instanceId).stream()
                    .anyMatch(item -> "UNKNOWN".equals(item.getStatus()));
        } catch (Exception ignored) {
            return false;
        }
    }
}
