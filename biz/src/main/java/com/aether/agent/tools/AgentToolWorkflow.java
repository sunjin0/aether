package com.aether.agent.tools;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMcpServer;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.entity.AgentToolCallLog;
import com.aether.agent.executor.ToolExecutionContext;
import com.aether.agent.executor.ToolExecutionResult;
import com.aether.agent.executor.ToolExecutorFactory;
import com.aether.agent.model.ModelChatResponse;
import com.aether.agent.security.ToolCallRiskAnalyzer;
import com.aether.agent.service.AgentMcpServerService;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.service.AgentToolCallLogService;
import com.aether.agent.service.AgentToolService;
import com.aether.agent.tools.ToolCallParser.ToolCall;
import com.aether.agent.tools.core.Tool;
import com.aether.agent.tools.core.ToolRegistry;
import com.aether.agent.tools.entity.ToolResult;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Agent 工具调用工作流。
 *
 * <p>聊天服务只负责会话编排和模型请求；工具解析、MCP 审批、执行和审计均在此集中处理，
 * 避免同一套安全逻辑散落在聊天主流程中。</p>
 */
@Component
public class AgentToolWorkflow {
    private static final Logger log = LoggerFactory.getLogger(AgentToolWorkflow.class);
    private static final String MCP_APPROVAL_TYPE = "mcp_tool_approval";
    private static final String TOOL_APPROVAL_GRANT_KEY_PREFIX = "agent:tool-approval:";
    private static final long TOOL_APPROVAL_GRANT_TTL_MINUTES = 10;
    private static final int STATUS_SUCCESS = 0;
    private static final int STATUS_FAILED = 1;
    private static final int STATUS_SECURITY_BLOCK = 3;
    private static final int STATUS_PENDING_APPROVAL = 4;

    private final ToolCallParser toolCallParser;
    private final AgentToolCatalog toolCatalog;
    private final AgentToolService agentToolService;
    private final AgentToolCallLogService toolCallLogService;
    private final AgentMcpServerService mcpServerService;
    private final AgentMessageService messageService;
    private final ToolExecutorFactory executorFactory;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ToolRegistry toolRegistry;
    private final ToolCallRiskAnalyzer riskAnalyzer = new ToolCallRiskAnalyzer();

    public AgentToolWorkflow(ToolCallParser toolCallParser, AgentToolCatalog toolCatalog,
                             AgentToolService agentToolService, AgentToolCallLogService toolCallLogService,
                             AgentMcpServerService mcpServerService, AgentMessageService messageService,
                             ToolExecutorFactory executorFactory, RedisTemplate<String, Object> redisTemplate,
                             ToolRegistry toolRegistry) {
        this.toolCallParser = toolCallParser;
        this.toolCatalog = toolCatalog;
        this.agentToolService = agentToolService;
        this.toolCallLogService = toolCallLogService;
        this.mcpServerService = mcpServerService;
        this.messageService = messageService;
        this.executorFactory = executorFactory;
        this.redisTemplate = redisTemplate;
        this.toolRegistry = toolRegistry;
    }

    /** 解析模型返回的 function/tool calls，异常格式由解析器降级为空列表。 */
    public List<ToolCall> parseCalls(ModelChatResponse response) {
        return toolCallParser.parse(response);
    }

    /** 返回请求模型时可公开的工具定义。 */
    public List<AgentTool> getRequestTools(String agentId) {
        return toolCatalog.getRequestTools(agentId);
    }

    /** 返回 Agent 已绑定且处于可用状态的工具。 */
    public List<AgentTool> getBoundTools(String agentId) {
        return toolCatalog.getBoundTools(agentId);
    }

    /** 工具或绑定关系变更时，使指定 Agent 的工具缓存立即失效。 */
    public void evictToolCache(String agentId) {
        toolCatalog.evict(agentId);
    }

    /** 工具配置变更时，清理所有绑定该工具的 Agent 缓存。 */
    public void evictToolCacheByToolId(String toolId) {
        toolCatalog.evictByToolId(toolId);
    }

    /** 判断模型是否请求了平台内置工具（例如询问用户）。 */
    public boolean hasInternalCall(ModelChatResponse response) {
        for (ToolCall call : parseCalls(response)) {
            if (toolRegistry.getHandler(call.getName()) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 执行第一个平台内置工具。内置工具会直接产生会话交互消息，不经过 MCP 审批。
     */
    public ToolResult executeInternalCall(String conversationId, ModelChatResponse response) {
        for (ToolCall call : parseCalls(response)) {
            Tool handler = toolRegistry.getHandler(call.getName());
            if (handler != null) {
                return handler.handle(conversationId, call.getArguments());
            }
        }
        return null;
    }

    /** 从询问用户工具参数中提取可展示的问题，供聊天层进行容错提示。 */
    public String extractQuestionText(ModelChatResponse response) {
        List<ToolCall> calls = parseCalls(response);
        if (calls.isEmpty()) {
            return null;
        }
        Object questions = calls.get(0).getArguments().get("questions");
        if (questions instanceof List && !((List<?>) questions).isEmpty()) {
            Object first = ((List<?>) questions).get(0);
            if (first instanceof Map) {
                Object question = ((Map<?, ?>) first).get("question");
                if (question != null && StringUtils.isNotBlank(question.toString())) {
                    return StringUtils.abbreviate(question.toString(), 1000);
                }
            }
        }
        return null;
    }

    /**
     * 执行模型请求的 MCP 工具，并为每次调用持久化审计记录。
     * 单个工具失败不会阻断其余调用，避免一个不稳定的 MCP 服务影响整轮对话。
     */
    public List<ToolExecutionResult> executeMcpCalls(ModelChatResponse response, AgentDefinition agent,
                                                      String userId, String runId) {
        List<ToolExecutionResult> results = new ArrayList<>();
        Map<String, AgentTool> toolMap = new HashMap<>();
        for (AgentTool tool : getBoundTools(agent.getId())) {
            toolMap.put(tool.getName(), tool);
        }

        for (ToolCall call : parseCalls(response)) {
            AgentTool tool = toolMap.get(call.getName());
            if (tool == null) {
                ToolExecutionResult failure = ToolExecutionResult.failure("工具未找到: " + call.getName(), STATUS_FAILED);
                failure.setToolCallId(call.getId());
                failure.setRequestMethod("MCP tools/call");
                results.add(failure);
                saveAudit(runId, call, null, agent.getId(), failure);
                continue;
            }

            ToolExecutionResult result;
            try {
                result = executeMcpTool(tool, call.getArguments(), runId, userId, agent.getId());
            } catch (Exception e) {
                result = ToolExecutionResult.failure(e.getMessage(), STATUS_FAILED);
                result.setRequestMethod("MCP tools/call");
            }
            result.setToolCallId(call.getId());
            results.add(result);
            saveAudit(runId, call, tool, agent.getId(), result);
        }
        return results;
    }

    /**
     * 为首次 MCP 调用创建确认交互。高风险工具会在前端配置中携带风险说明和命令预览。
     */
    public AgentMessage createMcpApproval(String conversationId, ModelChatResponse response,
                                          AgentDefinition agent, String userId, String runId) {
        List<ToolCall> calls = parseCalls(response);
        if (calls.isEmpty()) {
            return null;
        }
        ToolCall call = calls.get(0);
        AgentTool tool = findBoundTool(agent.getId(), call.getName());
        if (tool == null || hasActiveGrant(userId, agent.getId(), tool.getId())) {
            return null;
        }

        ToolCallRiskAnalyzer.Risk risk = riskAnalyzer.analyze(tool, call.getArguments());
        String requestUrl = resolveMcpRequestUrl(tool);
        AgentToolCallLog audit = savePendingAudit(runId, call, tool, agent.getId(), requestUrl);
        boolean highRisk = "high".equals(risk.getLevel());
        String prompt = highRisk ? "AI 请求执行高危 MCP 工具操作，请核对调用详情后确认。"
                : "AI 请求调用 MCP 工具，请核对调用详情后确认。";

        JSONObject config = new JSONObject();
        config.put("type", "group");
        config.put("layout", "confirm");
        config.put("question", "请确认 MCP 工具调用");
        config.put("questions", buildApprovalQuestions(prompt));
        config.put("approvalType", MCP_APPROVAL_TYPE);
        config.put("auditLogId", audit.getId());
        config.put("runId", runId);
        config.put("toolId", tool.getId());
        config.put("toolCallId", call.getId());
        config.put("toolName", call.getName());
        config.put("arguments", call.getArguments());
        config.put("riskLevel", risk.getLevel());
        config.put("riskReason", risk.getReason());
        config.put("approval", buildApprovalDetail(tool, call, requestUrl, risk, audit.getId()));

        AgentMessage message = new AgentMessage();
        message.setConversationId(conversationId);
        message.setRole("assistant");
        message.setMessageType("interaction");
        message.setInteractionType("group");
        message.setInteractionStatus("pending");
        message.setContent(config.getString("question"));
        message.setQuestionConfig(config.toJSONString());
        messageService.save(message);
        return message;
    }

    /** 在用户确认后执行 MCP 工具，并将执行结果重新包装为模型可识别的 tool call。 */
    public ApprovalExecution executeApprovedMcpTool(AgentMessage question, Map<String, Object> answer,
                                                      AgentDefinition agent, String userId) {
        JSONObject config = JSONObject.parseObject(question.getQuestionConfig());
        if (!MCP_APPROVAL_TYPE.equals(config.getString("approvalType"))) {
            return null;
        }
        String decision = resolveApprovalDecision(answer);
        boolean confirmed = "once".equals(decision) || "allow_10m".equals(decision);
        String runId = config.getString("runId");
        String toolCallId = config.getString("toolCallId");
        String toolName = config.getString("toolName");
        AgentTool tool = agentToolService.getById(config.getString("toolId"));
        Map<String, Object> arguments = getArguments(config);

        ToolExecutionResult result;
        if (!confirmed) {
            result = ToolExecutionResult.failure("用户拒绝执行此 MCP 工具调用", STATUS_SECURITY_BLOCK);
        } else if (!isAvailable(tool)) {
            result = ToolExecutionResult.failure("待确认的工具已不存在或被禁用", STATUS_FAILED);
        } else {
            try {
                result = executeMcpTool(tool, arguments, runId, userId, agent.getId());
            } catch (Exception e) {
                result = ToolExecutionResult.failure("MCP 工具执行失败: " + e.getMessage(), STATUS_FAILED);
            }
        }
        result.setToolCallId(toolCallId);
        if ("allow_10m".equals(decision) && tool != null) {
            saveGrant(userId, agent.getId(), tool.getId());
        }
        updateApprovalAudit(config.getString("auditLogId"), result, confirmed);
        return new ApprovalExecution(runId, buildToolCallResponse(toolCallId, toolName, arguments), result);
    }

    /**
     * 工作流人工确认节点的受控入口。调用方必须先将实例置为 WAITING_USER，
     * 本方法只负责复用同一执行器、十分钟授权与工具审计，不接受未确认的调用。
     */
    public ToolExecutionResult executeWorkflowApprovedMcpTool(String toolId, String toolName,
                                                               Map<String, Object> arguments, String runId,
                                                               String userId, String agentId, boolean allowTenMinutes,
                                                               String idempotencyKey) {
        AgentTool tool = agentToolService.getById(toolId);
        ToolCall call = new ToolCall("workflow-" + runId, toolName,
                arguments == null ? new HashMap<String, Object>() : arguments);
        ToolExecutionResult result;
        if (!isAvailable(tool)) {
            result = ToolExecutionResult.failure("待确认的工具已不存在或被禁用", STATUS_FAILED);
        } else {
            try { result = executeMcpTool(tool, call.getArguments(), runId, userId, agentId, idempotencyKey); }
            catch (Exception e) { result = ToolExecutionResult.failure("MCP 工具执行失败: " + e.getMessage(), STATUS_FAILED); }
        }
        result.setToolCallId(call.getId());
        saveAudit(runId, call, tool, agentId, result);
        if (allowTenMinutes && tool != null) saveGrant(userId, agentId, tool.getId());
        return result;
    }

    private ToolExecutionResult executeMcpTool(AgentTool tool, Map<String, Object> arguments,
                                               String runId, String userId, String agentDefinitionId) {
        return executeMcpTool(tool, arguments, runId, userId, agentDefinitionId, null);
    }

    private ToolExecutionResult executeMcpTool(AgentTool tool, Map<String, Object> arguments,
                                               String runId, String userId, String agentDefinitionId, String idempotencyKey) {
        ToolExecutionContext context = new ToolExecutionContext();
        context.setTool(tool);
        context.setArguments(arguments);
        context.setRunId(runId);
        context.setUserId(userId);
        context.setAgentDefinitionId(agentDefinitionId);
        context.setIdempotencyKey(idempotencyKey);
        return executorFactory.getExecutor("mcp").execute(context);
    }

    private AgentTool findBoundTool(String agentId, String name) {
        for (AgentTool tool : getBoundTools(agentId)) {
            if (name.equals(tool.getName())) {
                return tool;
            }
        }
        return null;
    }

    private JSONArray buildApprovalQuestions(String prompt) {
        JSONObject question = new JSONObject();
        question.put("id", "decision");
        question.put("type", "choice");
        question.put("question", prompt);
        question.put("multiple", false);
        question.put("options", new JSONArray()
                .fluentAdd(new JSONObject().fluentPut("id", "once").fluentPut("label", "仅本次执行").fluentPut("value", "once"))
                .fluentAdd(new JSONObject().fluentPut("id", "allow_10m").fluentPut("label", "当前工具 10 分钟内免确认").fluentPut("value", "allow_10m"))
                .fluentAdd(new JSONObject().fluentPut("id", "reject").fluentPut("label", "拒绝执行").fluentPut("value", "reject")));
        return new JSONArray().fluentAdd(question);
    }

    /** 构造前端确认面板所需详情；服务端续跑仍使用 config 顶层字段。 */
    private JSONObject buildApprovalDetail(AgentTool tool, ToolCall call, String requestUrl,
                                           ToolCallRiskAnalyzer.Risk risk, String auditLogId) {
        JSONObject approval = new JSONObject();
        approval.put("tool", new JSONObject().fluentPut("id", tool.getId())
                .fluentPut("name", call.getName()).fluentPut("mcpServerId", tool.getMcpServerId()));
        approval.put("request", new JSONObject().fluentPut("url", requestUrl)
                .fluentPut("method", "MCP tools/call").fluentPut("arguments", call.getArguments()));
        approval.put("risk", new JSONObject().fluentPut("level", risk.getLevel())
                .fluentPut("reason", risk.getReason())
                .fluentPut("commandPreview", truncate(risk.getCommandPreview(), 4000)));
        approval.put("auditLogId", auditLogId);
        approval.put("authorizationOptions", new JSONArray()
                .fluentAdd(new JSONObject().fluentPut("value", "once").fluentPut("ttlSeconds", 0))
                .fluentAdd(new JSONObject().fluentPut("value", "allow_10m").fluentPut("ttlSeconds", 600))
                .fluentAdd(new JSONObject().fluentPut("value", "reject").fluentPut("ttlSeconds", 0)));
        return approval;
    }

    private Map<String, Object> getArguments(JSONObject config) {
        JSONObject jsonArguments = config.getJSONObject("arguments");
        return jsonArguments == null ? new HashMap<String, Object>() : jsonArguments.toJavaObject(Map.class);
    }

    private ModelChatResponse buildToolCallResponse(String toolCallId, String toolName, Map<String, Object> arguments) {
        JSONObject function = new JSONObject();
        function.put("name", toolName);
        function.put("arguments", JSON.toJSONString(arguments));
        JSONObject call = new JSONObject();
        call.put("id", toolCallId);
        call.put("type", "function");
        call.put("function", function);
        ModelChatResponse response = new ModelChatResponse();
        response.setToolCalls(new JSONArray().fluentAdd(call).toJSONString());
        return response;
    }

    private String resolveApprovalDecision(Map<String, Object> answer) {
        if (answer == null || !(answer.get("answers") instanceof Map)) {
            return "reject";
        }
        Map<?, ?> answers = (Map<?, ?>) answer.get("answers");
        Object decision = answers.get("decision");
        if (decision instanceof Map) {
            Object selected = ((Map<?, ?>) decision).get("selected");
            if (selected != null && ("once".equals(selected.toString()) || "allow_10m".equals(selected.toString()))) {
                return selected.toString();
            }
        }
        // 兼容旧版确认卡片：旧前端仅提交 confirm.confirmed。
        Object confirm = answers.get("confirm");
        return confirm instanceof Map && Boolean.TRUE.equals(((Map<?, ?>) confirm).get("confirmed")) ? "once" : "reject";
    }

    private boolean isAvailable(AgentTool tool) {
        return tool != null && !Boolean.TRUE.equals(tool.getDeleted()) && Integer.valueOf(1).equals(tool.getStatus());
    }

    private boolean hasActiveGrant(String userId, String agentId, String toolId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(grantKey(userId, agentId, toolId)));
        } catch (Exception e) {
            log.warn("检查工具授权失败, 按未授权处理: userId={}, agentId={}, toolId={}", userId, agentId, toolId, e);
            return false;
        }
    }

    private void saveGrant(String userId, String agentId, String toolId) {
        try {
            redisTemplate.opsForValue().set(grantKey(userId, agentId, toolId), "approved",
                    TOOL_APPROVAL_GRANT_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("保存工具授权失败: userId={}, agentId={}, toolId={}", userId, agentId, toolId, e);
        }
    }

    private String grantKey(String userId, String agentId, String toolId) {
        return TOOL_APPROVAL_GRANT_KEY_PREFIX + userId + ":" + agentId + ":" + toolId;
    }

    private String resolveMcpRequestUrl(AgentTool tool) {
        if (tool == null || StringUtils.isBlank(tool.getMcpServerId())) {
            return null;
        }
        AgentMcpServer server = mcpServerService.getById(tool.getMcpServerId());
        return server == null ? null : server.getBaseUrl();
    }

    private AgentToolCallLog savePendingAudit(String runId, ToolCall call, AgentTool tool,
                                               String agentId, String requestUrl) {
        ToolExecutionResult pending = ToolExecutionResult.failure("等待用户确认，尚未发送到 MCP 服务", STATUS_PENDING_APPROVAL);
        pending.setRequestUrl(requestUrl);
        pending.setRequestMethod("MCP tools/call");
        return saveAudit(runId, call, tool, agentId, pending);
    }

    /** 保存调用审计，统一截断大字段，避免日志表因异常响应内容写入失败。 */
    private AgentToolCallLog saveAudit(String runId, ToolCall call, AgentTool tool,
                                       String agentId, ToolExecutionResult result) {
        AgentToolCallLog log = new AgentToolCallLog();
        log.setRunId(runId);
        log.setToolCallId(call.getId());
        log.setToolName(call.getName());
        log.setArguments(truncate(JSON.toJSONString(call.getArguments()), 65536));
        log.setToolId(tool == null ? null : tool.getId());
        // agent_definition_id 为 NOT NULL；来源缺失时兜底使用运行标识，避免约束错误掩盖真实调用错误。
        log.setAgentDefinitionId(StringUtils.defaultIfBlank(agentId, StringUtils.defaultIfBlank(runId, "unknown")));
        log.setRequestUrl(truncate(result.getRequestUrl(), 2048));
        log.setRequestMethod(result.getRequestMethod());
        log.setRequestHeaders(result.getRequestHeaders());
        log.setRequestBody(truncate(result.getRequestBody(), 65536));
        log.setResponseStatus(result.getHttpStatus());
        log.setResponseBody(truncate(result.getRawResponse(), 65536));
        log.setLatencyMs(result.getLatencyMs());
        log.setStatus(result.getStatus());
        log.setErrorMsg(truncate(result.getErrorMsg(), 1024));
        toolCallLogService.save(log);
        return log;
    }

    private void updateApprovalAudit(String auditLogId, ToolExecutionResult result, boolean confirmed) {
        if (StringUtils.isBlank(auditLogId)) {
            return;
        }
        AgentToolCallLog update = new AgentToolCallLog();
        update.setId(auditLogId);
        update.setRequestUrl(truncate(result.getRequestUrl(), 2048));
        update.setRequestMethod(result.getRequestMethod());
        update.setRequestHeaders(result.getRequestHeaders());
        update.setRequestBody(truncate(result.getRequestBody(), 65536));
        update.setResponseStatus(result.getHttpStatus());
        update.setResponseBody(truncate(result.getRawResponse(), 65536));
        update.setLatencyMs(result.getLatencyMs());
        update.setStatus(result.getStatus());
        // 成功时写入空串，确保 MyBatis 更新后不会保留“等待确认”的旧错误文案。
        update.setErrorMsg(truncate(confirmed ? StringUtils.defaultString(result.getErrorMsg()) : "用户拒绝执行", 1024));
        toolCallLogService.updateById(update);
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /** 用户确认后的执行结果，供聊天服务继续构造下一轮模型上下文。 */
    public static class ApprovalExecution {
        private final String runId;
        private final ModelChatResponse toolCallResponse;
        private final ToolExecutionResult result;

        public ApprovalExecution(String runId, ModelChatResponse toolCallResponse, ToolExecutionResult result) {
            this.runId = runId;
            this.toolCallResponse = toolCallResponse;
            this.result = result;
        }

        public String getRunId() { return runId; }
        public ModelChatResponse getToolCallResponse() { return toolCallResponse; }
        public ToolExecutionResult getResult() { return result; }
    }
}
