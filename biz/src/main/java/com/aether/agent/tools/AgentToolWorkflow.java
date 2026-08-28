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
import com.aether.agent.service.AgentRunService;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.service.AgentToolCallLogService;
import com.aether.agent.service.AgentToolService;
import com.aether.agent.service.ToolRouterService;
import com.aether.sys.entity.User;
import com.aether.sys.service.UserService;
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
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import javax.annotation.PreDestroy;

/**
 * Agent 工具调用工作流。
 *
 * <p>聊天服务只负责会话编排和模型请求；工具解析、MCP 审批、执行和审计均在此集中处理，
 * 避免同一套安全逻辑散落在聊天主流程中。</p>
 */
@Component
public class AgentToolWorkflow {
    private static final int MAX_TOOL_SCHEMA_CHARS = 16000;
    private static final Logger log = LoggerFactory.getLogger(AgentToolWorkflow.class);
    private static final String MCP_APPROVAL_TYPE = "mcp_tool_approval";
    private static final String TOOL_APPROVAL_GRANT_KEY_PREFIX = "agent:tool-approval:";
    private static final long TOOL_APPROVAL_GRANT_TTL_MINUTES = 10;
    private static final String APPROVAL_ASK = "ask";
    private static final String APPROVAL_RISKY = "risky";
    private static final String APPROVAL_NEVER = "never";
    private static final int STATUS_SUCCESS = 0;
    private static final int STATUS_FAILED = 1;
    private static final int STATUS_SECURITY_BLOCK = 3;
    private static final int STATUS_PENDING_APPROVAL = 4;
    private static final int MAX_PARALLEL_READ_ONLY_CALLS = 4;

    private final ToolCallParser toolCallParser;
    private final AgentToolCatalog toolCatalog;
    private final AgentToolService agentToolService;
    private final AgentToolCallLogService toolCallLogService;
    private final AgentMcpServerService mcpServerService;
    private final AgentRunService agentRunService;
    private final AgentMessageService messageService;
    private final ToolExecutorFactory executorFactory;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ToolRegistry toolRegistry;
    private final ToolRouterService toolRouterService;
    private final ToolCallRiskAnalyzer riskAnalyzer = new ToolCallRiskAnalyzer();
    private final ExecutorService readOnlyToolExecutor = new ThreadPoolExecutor(2, 8, 30L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(64), new ThreadPoolExecutor.CallerRunsPolicy());

    @Autowired(required = false)
    private UserService userService;

    /**
     * 创建 {@code AgentToolWorkflow} 实例。
     */
    public AgentToolWorkflow(ToolCallParser toolCallParser, AgentToolCatalog toolCatalog,
                             AgentToolService agentToolService, AgentToolCallLogService toolCallLogService,
                             AgentMcpServerService mcpServerService, AgentRunService agentRunService, AgentMessageService messageService,
                             ToolExecutorFactory executorFactory, RedisTemplate<String, Object> redisTemplate,
                             ToolRegistry toolRegistry, ToolRouterService toolRouterService) {
        this.toolCallParser = toolCallParser;
        this.toolCatalog = toolCatalog;
        this.agentToolService = agentToolService;
        this.toolCallLogService = toolCallLogService;
        this.mcpServerService = mcpServerService;
        this.agentRunService = agentRunService;
        this.messageService = messageService;
        this.executorFactory = executorFactory;
        this.redisTemplate = redisTemplate;
        this.toolRegistry = toolRegistry;
        this.toolRouterService = toolRouterService;
    }

    /**
     * 解析模型返回的 function/tool calls，异常格式由解析器降级为空列表。
     */
    public List<ToolCall> parseCalls(ModelChatResponse response) {
        return toolCallParser.parse(response);
    }

    /**
     * 返回请求模型时可公开的工具定义。
     */
    public List<AgentTool> getRequestTools(String agentId) {
        return toolCatalog.getRequestTools(agentId);
    }

    /**
     * Skill 运行时使用已冻结的 MCP 工具，并保留平台内置交互工具。
     */
    public List<AgentTool> getRequestTools(List<AgentTool> scopedTools) {
        return getRequestTools(scopedTools, null, java.util.Collections.<String>emptySet());
    }

    /**
     * 返回请求模型时可公开的工具定义。内置交互工具与 Skill required 工具常驻保留，
     * 其余工具按关键字匹配与 query 向量召回 Top-K 裁剪以节省上下文，未匹配的工具不携带；
     * query 为空时不裁剪。
     */
    public List<AgentTool> getRequestTools(List<AgentTool> scopedTools, String query, Set<String> requiredToolIds) {
        List<AgentTool> builtInTools = toolRegistry.getTools();
        List<AgentTool> candidates = new ArrayList<>(scopedTools == null
                ? java.util.Collections.<AgentTool>emptyList() : scopedTools);
        candidates.addAll(builtInTools);
        Set<String> protectedToolIds = new java.util.HashSet<>(requiredToolIds == null
                ? java.util.Collections.<String>emptySet() : requiredToolIds);
        for (AgentTool builtIn : builtInTools) {
            if (builtIn != null && builtIn.getId() != null) protectedToolIds.add(builtIn.getId());
        }
        List<AgentTool> routed = toolRouterService.route(candidates, protectedToolIds, query);
        List<AgentTool> tools = new ArrayList<>();
        int schemaChars = 0;
        for (AgentTool tool : routed) {
            int size = StringUtils.length(tool.getName()) + StringUtils.length(tool.getDescription())
                    + StringUtils.length(tool.getParametersSchema()) + StringUtils.length(tool.getMcpInputSchema());
            if (!tools.isEmpty() && schemaChars + size > MAX_TOOL_SCHEMA_CHARS) {
                continue;
            }
            tools.add(toModelVisibleTool(tool));
            schemaChars += size;
        }
        tools.sort(Comparator.comparing(AgentTool::getId, Comparator.nullsLast(String::compareTo)));
        return tools;
    }

    /**
     * 返回 Agent 已绑定且处于可用状态的工具。
     */
    public List<AgentTool> getBoundTools(String agentId) {
        return toolCatalog.getBoundTools(agentId);
    }

    /**
     * 工具或绑定关系变更时，使指定 Agent 的工具缓存立即失效。
     */
    public void evictToolCache(String agentId) {
        toolCatalog.evict(agentId);
    }

    /**
     * 工具配置变更时，清理所有绑定该工具的 Agent 缓存。
     */
    public void evictToolCacheByToolId(String toolId) {
        toolCatalog.evictByToolId(toolId);
    }

    /**
     * Revokes temporary approvals when a conversation's policy becomes stricter.
     */
    public void revokeTemporaryGrants(String userId, String agentId, String conversationId) {
        if (StringUtils.isAnyBlank(userId, agentId, conversationId)) return;
        try {
            Set<String> keys = redisTemplate.keys(TOOL_APPROVAL_GRANT_KEY_PREFIX + userId + ":" + agentId + ":" + conversationId + ":*");
            if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        } catch (Exception e) {
            log.warn("撤销工具临时授权失败: userId={}, agentId={}", userId, agentId, e);
        }
    }

    /**
     * 判断模型是否请求了平台内置工具（例如询问用户）。
     */
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

    /**
     * 从询问用户工具参数中提取可展示的问题，供聊天层进行容错提示。
     */
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

        for (ToolCall parsedCall : parseCalls(response)) {
            AgentTool tool = toolMap.get(parsedCall.getName());
            ToolCall call = withEmailDefaults(tool, parsedCall, userId);
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
     * 仅执行本次请求冻结作用域内的 MCP 工具，防止模型伪造调用绕过 Skill 收敛。
     */
    public List<ToolExecutionResult> executeMcpCalls(ModelChatResponse response, AgentDefinition agent,
                                                     String userId, String runId, List<AgentTool> scopedTools) {
        List<ToolExecutionResult> results = new ArrayList<>();
        Map<String, AgentTool> toolMap = new HashMap<>();
        for (AgentTool tool : scopedTools == null ? java.util.Collections.<AgentTool>emptyList() : scopedTools)
            toolMap.put(tool.getName(), tool);
        List<ToolCall> calls = parseCalls(response);
        if (canExecuteReadOnlyCallsInParallel(calls, toolMap)) {
            return executeReadOnlyCallsInParallel(calls, toolMap, agent, userId, runId);
        }
        for (ToolCall parsedCall : calls) {
            AgentTool tool = toolMap.get(parsedCall.getName());
            ToolCall call = withEmailDefaults(tool, parsedCall, userId);
            if (tool == null) {
                ToolExecutionResult failure = ToolExecutionResult.failure("工具未在本次 Skill 作用域内: " + call.getName(), STATUS_SECURITY_BLOCK);
                failure.setToolCallId(call.getId());
                results.add(failure);
                saveAudit(runId, call, null, agent.getId(), failure);
                continue;
            }
            ToolExecutionResult result = executeMcpCall(tool, call, runId, userId, agent.getId());
            results.add(result);
            saveAudit(runId, call, tool, agent.getId(), result);
        }
        return results;
    }

    /**
     * 判断是否可以执行ReadOnlyCallsInParallel。
     */
    private boolean canExecuteReadOnlyCallsInParallel(List<ToolCall> calls, Map<String, AgentTool> toolMap) {
        if (calls == null || calls.size() < 2 || calls.size() > MAX_PARALLEL_READ_ONLY_CALLS) return false;
        for (ToolCall call : calls) {
            AgentTool tool = toolMap.get(call.getName());
            if (tool == null || !"low".equals(riskAnalyzer.analyze(tool, call.getArguments()).getLevel())) return false;
        }
        return true;
    }

    /**
     * 执行ReadOnlyCallsInParallel。
     */
    private List<ToolExecutionResult> executeReadOnlyCallsInParallel(List<ToolCall> calls,
                                                                     Map<String, AgentTool> toolMap,
                                                                     AgentDefinition agent, String userId,
                                                                     String runId) {
        long startedAt = System.currentTimeMillis();
        List<Callable<ToolExecutionResult>> tasks = new ArrayList<>();
        for (ToolCall call : calls) {
            AgentTool tool = toolMap.get(call.getName());
            tasks.add(() -> executeMcpCall(tool, call, runId, userId, agent.getId()));
        }
        List<ToolExecutionResult> results = new ArrayList<>();
        try {
            List<Future<ToolExecutionResult>> futures = readOnlyToolExecutor.invokeAll(tasks);
            for (int i = 0; i < futures.size(); i++) {
                ToolExecutionResult result;
                try {
                    result = futures.get(i).get();
                } catch (Exception e) {
                    result = ToolExecutionResult.failure(e.getMessage(), STATUS_FAILED);
                    result.setToolCallId(calls.get(i).getId());
                }
                results.add(result);
                saveAudit(runId, calls.get(i), toolMap.get(calls.get(i).getName()), agent.getId(), result);
            }
            log.info("并行只读 MCP 工具完成: runId={}, calls={}, duration={}ms", runId, calls.size(),
                    System.currentTimeMillis() - startedAt);
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并行工具调用被中断", e);
        }
    }

    /**
     * 执行McpCall。
     */
    private ToolExecutionResult executeMcpCall(AgentTool tool, ToolCall call, String runId,
                                               String userId, String agentId) {
        ToolExecutionResult result;
        try {
            result = executeMcpTool(tool, call.getArguments(), runId, userId, agentId);
        } catch (Exception e) {
            result = ToolExecutionResult.failure(e.getMessage(), STATUS_FAILED);
        }
        result.setToolCallId(call.getId());
        return result;
    }

    /**
     * 处理shutdownReadOnlyToolExecutor。
     */
    @PreDestroy
    public void shutdownReadOnlyToolExecutor() {
        readOnlyToolExecutor.shutdown();
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
        ToolCall call = null;
        AgentTool tool = null;
        for (ToolCall candidate : calls) {
            AgentTool candidateTool = findBoundTool(agent.getId(), candidate.getName());
            if (candidateTool != null && shouldRequestApproval(runId, candidateTool, candidate, userId, agent.getId())) {
                call = candidate;
                tool = candidateTool;
                break;
            }
        }
        if (call == null) {
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
        // DeepSeek thinking mode requires this exact assistant tool-call message when continuing.
        config.put("modelContent", response.getContent());
        config.put("modelReasoningContent", response.getReasoningContent());
        config.put("modelToolCalls", response.getToolCalls());
        config.put("riskLevel", risk.getLevel());
        config.put("riskReason", risk.getReason());
        config.put("riskEvidence", risk.getEvidence());
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

    /**
     * 使用本次运行冻结工具集创建审批卡片。
     */
    public AgentMessage createMcpApproval(String conversationId, ModelChatResponse response,
                                          AgentDefinition agent, String userId, String runId, List<AgentTool> scopedTools) {
        List<ToolCall> calls = parseCalls(response);
        if (calls.isEmpty()) return null;
        ToolCall call = null;
        AgentTool tool = null;
        for (ToolCall candidate : calls) {
            for (AgentTool item : scopedTools == null ? java.util.Collections.<AgentTool>emptyList() : scopedTools) {
                if (candidate.getName().equals(item.getName()) && shouldRequestApproval(runId, item, candidate, userId, agent.getId())) {
                    call = withEmailDefaults(item, candidate, userId);
                    tool = item;
                    break;
                }
            }
            if (tool != null) break;
        }
        if (tool == null) return null;
        ToolCallRiskAnalyzer.Risk risk = riskAnalyzer.analyze(tool, call.getArguments());
        String requestUrl = resolveMcpRequestUrl(tool);
        AgentToolCallLog audit = savePendingAudit(runId, call, tool, agent.getId(), requestUrl);
        JSONObject config = new JSONObject();
        config.put("type", "group");
        config.put("layout", "confirm");
        config.put("question", "请确认 MCP 工具调用");
        config.put("questions", buildApprovalQuestions("high".equals(risk.getLevel()) ? "AI 请求执行高危 MCP 工具操作，请核对调用详情后确认。" : "AI 请求调用 MCP 工具，请核对调用详情后确认。"));
        config.put("approvalType", MCP_APPROVAL_TYPE);
        config.put("auditLogId", audit.getId());
        config.put("runId", runId);
        config.put("toolId", tool.getId());
        config.put("toolCallId", call.getId());
        config.put("toolName", call.getName());
        config.put("arguments", call.getArguments());
        config.put("riskLevel", risk.getLevel());
        config.put("riskReason", risk.getReason());
        config.put("riskEvidence", risk.getEvidence());
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

    /**
     * 在用户确认后执行 MCP 工具，并将执行结果重新包装为模型可识别的 tool call。
     */
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
        arguments = withEmailDefaults(tool, new ToolCall(toolCallId, toolName, arguments), userId).getArguments();

        ToolExecutionResult result;
        if (!confirmed) {
            result = ToolExecutionResult.failure("用户拒绝执行此 MCP 工具调用", STATUS_SECURITY_BLOCK);
        } else if (!isAvailable(tool) || !isToolInRunScope(runId, tool.getId(), agent.getId())) {
            result = ToolExecutionResult.failure("待确认工具已不可用或不在本次运行授权范围内", STATUS_SECURITY_BLOCK);
        } else {
            try {
                result = executeMcpTool(tool, arguments, runId, userId, agent.getId());
            } catch (Exception e) {
                result = ToolExecutionResult.failure("MCP 工具执行失败: " + e.getMessage(), STATUS_FAILED);
            }
        }
        result.setToolCallId(toolCallId);
        if ("allow_10m".equals(decision) && tool != null && !isEmailTool(tool)) {
            saveGrant(userId, agent.getId(), tool.getId(), question.getConversationId());
        }
        updateApprovalAudit(config.getString("auditLogId"), result, confirmed);
        return new ApprovalExecution(runId, buildToolCallResponse(config, toolCallId, toolName, arguments), result);
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
            try {
                result = executeMcpTool(tool, call.getArguments(), runId, userId, agentId, idempotencyKey);
            } catch (Exception e) {
                result = ToolExecutionResult.failure("MCP 工具执行失败: " + e.getMessage(), STATUS_FAILED);
            }
        }
        result.setToolCallId(call.getId());
        saveAudit(runId, call, tool, agentId, result);
        if (allowTenMinutes && tool != null) saveGrant(userId, agentId, tool.getId(), null);
        return result;
    }

    /**
     * 执行McpTool。
     */
    private ToolExecutionResult executeMcpTool(AgentTool tool, Map<String, Object> arguments,
                                               String runId, String userId, String agentDefinitionId) {
        return executeMcpTool(tool, arguments, runId, userId, agentDefinitionId, null);
    }

    /**
     * 执行McpTool。
     */
    private ToolExecutionResult executeMcpTool(AgentTool tool, Map<String, Object> arguments,
                                               String runId, String userId, String agentDefinitionId, String idempotencyKey) {
        ToolExecutionContext context = new ToolExecutionContext();
        context.setTool(tool);
        context.setArguments(arguments);
        context.setRunId(runId);
        context.setUserId(userId);
        context.setAgentDefinitionId(agentDefinitionId);
        context.setIdempotencyKey(idempotencyKey);
        if (StringUtils.isNotBlank(runId)) {
            com.aether.agent.entity.AgentRun run = agentRunService.getById(runId);
            if (run != null) {
                context.setApplicationId(run.getApplicationId());
                context.setProductProfileId(run.getProductProfileId());
                context.setServiceAccountId(run.getServiceAccountId());
                context.setTrustedContext(run.getTrustedContext());
            }
        }
        return executorFactory.getExecutor("mcp").execute(context);
    }

    /**
     * 查找BoundTool。
     */
    private AgentTool findBoundTool(String agentId, String name) {
        for (AgentTool tool : getBoundTools(agentId)) {
            if (name.equals(tool.getName())) {
                return tool;
            }
        }
        return null;
    }

    /**
     * 构建ApprovalQuestions。
     */
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

    /**
     * 构造前端确认面板所需详情；服务端续跑仍使用 config 顶层字段。
     */
    private JSONObject buildApprovalDetail(AgentTool tool, ToolCall call, String requestUrl,
                                           ToolCallRiskAnalyzer.Risk risk, String auditLogId) {
        JSONObject approval = new JSONObject();
        approval.put("tool", new JSONObject().fluentPut("id", tool.getId())
                .fluentPut("name", call.getName()).fluentPut("mcpServerId", tool.getMcpServerId()));
        approval.put("request", new JSONObject().fluentPut("url", requestUrl)
                .fluentPut("method", "MCP tools/call").fluentPut("arguments", call.getArguments()));
        approval.put("risk", new JSONObject().fluentPut("level", risk.getLevel())
                .fluentPut("reason", risk.getReason())
                .fluentPut("commandPreview", truncate(risk.getCommandPreview(), 4000))
                .fluentPut("evidence", risk.getEvidence()));
        approval.put("auditLogId", auditLogId);
        approval.put("authorizationOptions", new JSONArray()
                .fluentAdd(new JSONObject().fluentPut("value", "once").fluentPut("ttlSeconds", 0))
                .fluentAdd(new JSONObject().fluentPut("value", "allow_10m").fluentPut("ttlSeconds", 600))
                .fluentAdd(new JSONObject().fluentPut("value", "reject").fluentPut("ttlSeconds", 0)));
        return approval;
    }

    /**
     * 获取Arguments。
     */
    private Map<String, Object> getArguments(JSONObject config) {
        JSONObject jsonArguments = config.getJSONObject("arguments");
        return jsonArguments == null ? new HashMap<String, Object>() : jsonArguments.toJavaObject(Map.class);
    }

    /**
     * 构建ToolCallResponse。
     */
    private ModelChatResponse buildToolCallResponse(JSONObject config, String toolCallId, String toolName,
                                                    Map<String, Object> arguments) {
        String toolCalls = config.getString("modelToolCalls");
        JSONObject function = new JSONObject();
        function.put("name", toolName);
        function.put("arguments", JSON.toJSONString(arguments));
        JSONObject call = new JSONObject();
        call.put("id", toolCallId);
        call.put("type", "function");
        call.put("function", function);
        ModelChatResponse response = new ModelChatResponse();
        response.setContent(config.getString("modelContent"));
        response.setReasoningContent(config.getString("modelReasoningContent"));
        response.setToolCalls(StringUtils.defaultIfBlank(toolCalls, new JSONArray().fluentAdd(call).toJSONString()));
        return response;
    }

    /**
     * 解析ApprovalDecision。
     */
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

    /**
     * 判断是否为Available。
     */
    private boolean isAvailable(AgentTool tool) {
        return tool != null && !Boolean.TRUE.equals(tool.getDeleted()) && Integer.valueOf(1).equals(tool.getStatus());
    }

    /**
     * 判断是否拥有ActiveGrant。
     */
    private boolean hasActiveGrant(String userId, String agentId, String toolId, String conversationId) {
        if (StringUtils.isBlank(conversationId)) return false;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(grantKey(userId, agentId, toolId, conversationId)));
        } catch (Exception e) {
            log.warn("检查工具授权失败, 按未授权处理: userId={}, agentId={}, toolId={}", userId, agentId, toolId, e);
            return false;
        }
    }

    /**
     * 判断是否为ToolIn运行Scope。
     */
    private boolean isToolInRunScope(String runId, String toolId, String agentId) {
        com.aether.agent.entity.AgentRun run = agentRunService.getById(runId);
        if (run == null || !agentId.equals(run.getAgentDefinitionId()) || StringUtils.isBlank(run.getSkillSnapshot()))
            return false;
        try {
            JSONArray toolIds = JSONObject.parseObject(run.getSkillSnapshot()).getJSONArray("toolIds");
            return toolIds != null && toolIds.contains(toolId);
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * The run snapshot wins over mutable session settings. Legacy runs default to ask.
     */
    private boolean shouldRequestApproval(String runId, AgentTool tool, ToolCall call, String userId, String agentId) {
        String policy = APPROVAL_ASK;
        com.aether.agent.entity.AgentRun run = agentRunService.getById(runId);
        if (!isEmailTool(tool) && hasActiveGrant(userId, agentId, tool.getId(), run == null ? null : run.getConversationId())) return false;
        if (isEmailTool(tool)) return true;
        if (run != null && StringUtils.isNotBlank(run.getSkillSnapshot())) {
            try {
                policy = StringUtils.defaultIfBlank(JSONObject.parseObject(run.getSkillSnapshot()).getString("toolApprovalPolicy"), APPROVAL_ASK);
            } catch (Exception ignored) { /* legacy runs default to ask */ }
        }
        if (APPROVAL_NEVER.equals(policy)) {
            return false;
        }
        if (APPROVAL_RISKY.equals(policy)) {
            return "high".equals(riskAnalyzer.analyze(tool, call.getArguments()).getLevel());
        }
        return true;
    }

    /**
     * 保存Grant。
     */
    private void saveGrant(String userId, String agentId, String toolId, String conversationId) {
        if (StringUtils.isBlank(conversationId)) return;
        try {
            redisTemplate.opsForValue().set(grantKey(userId, agentId, toolId, conversationId), "approved",
                    TOOL_APPROVAL_GRANT_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("保存工具授权失败: userId={}, agentId={}, toolId={}", userId, agentId, toolId, e);
        }
    }

    /**
     * 处理grantKey。
     */
    private String grantKey(String userId, String agentId, String toolId, String conversationId) {
        return TOOL_APPROVAL_GRANT_KEY_PREFIX + userId + ":" + agentId + ":" + conversationId + ":" + toolId;
    }

    /**
     * 解析McpRequestUrl。
     */
    private String resolveMcpRequestUrl(AgentTool tool) {
        if (tool == null || StringUtils.isBlank(tool.getMcpServerId())) {
            return null;
        }
        AgentMcpServer server = mcpServerService.getById(tool.getMcpServerId());
        return server == null ? null : server.getBaseUrl();
    }

    /**
     * 保存PendingAudit。
     */
    private AgentToolCallLog savePendingAudit(String runId, ToolCall call, AgentTool tool,
                                              String agentId, String requestUrl) {
        ToolExecutionResult pending = ToolExecutionResult.failure("等待用户确认，尚未发送到 MCP 服务", STATUS_PENDING_APPROVAL);
        pending.setRequestUrl(requestUrl);
        pending.setRequestMethod("MCP tools/call");
        return saveAudit(runId, call, tool, agentId, pending);
    }

    /**
     * 保存调用审计，统一截断大字段，避免日志表因异常响应内容写入失败。
     */
    private AgentToolCallLog saveAudit(String runId, ToolCall call, AgentTool tool,
                                       String agentId, ToolExecutionResult result) {
        AgentToolCallLog log = new AgentToolCallLog();
        log.setRunId(runId);
        com.aether.agent.entity.AgentRun run = StringUtils.isBlank(runId) ? null : agentRunService.getById(runId);
        if (run != null) log.setApplicationId(run.getApplicationId());
        log.setToolCallId(call.getId());
        log.setToolName(call.getName());
        log.setArguments(redactSecrets(truncate(JSON.toJSONString(call.getArguments()), 65536)));
        log.setToolId(tool == null ? null : tool.getId());
        // agent_definition_id 为 NOT NULL；来源缺失时兜底使用运行标识，避免约束错误掩盖真实调用错误。
        log.setAgentDefinitionId(StringUtils.defaultIfBlank(agentId, StringUtils.defaultIfBlank(runId, "unknown")));
        log.setRequestUrl(truncate(result.getRequestUrl(), 2048));
        log.setRequestMethod(result.getRequestMethod());
        log.setRequestHeaders(redactSecrets(result.getRequestHeaders()));
        log.setRequestBody(redactSecrets(truncate(result.getRequestBody(), 65536)));
        log.setResponseStatus(result.getHttpStatus());
        log.setResponseBody(truncate(result.getRawResponse(), 65536));
        log.setLatencyMs(result.getLatencyMs());
        log.setStatus(result.getStatus());
        log.setErrorMsg(truncate(result.getErrorMsg(), 1024));
        toolCallLogService.save(log);
        return log;
    }

    /**
     * 更新ApprovalAudit。
     */
    private void updateApprovalAudit(String auditLogId, ToolExecutionResult result, boolean confirmed) {
        if (StringUtils.isBlank(auditLogId)) {
            return;
        }
        AgentToolCallLog update = new AgentToolCallLog();
        update.setId(auditLogId);
        update.setRequestUrl(truncate(result.getRequestUrl(), 2048));
        update.setRequestMethod(result.getRequestMethod());
        update.setRequestHeaders(redactSecrets(result.getRequestHeaders()));
        update.setRequestBody(redactSecrets(truncate(result.getRequestBody(), 65536)));
        update.setResponseStatus(result.getHttpStatus());
        update.setResponseBody(truncate(result.getRawResponse(), 65536));
        update.setLatencyMs(result.getLatencyMs());
        update.setStatus(result.getStatus());
        // 成功时写入空串，确保 MyBatis 更新后不会保留“等待确认”的旧错误文案。
        update.setErrorMsg(truncate(confirmed ? StringUtils.defaultString(result.getErrorMsg()) : "用户拒绝执行", 1024));
        toolCallLogService.updateById(update);
    }

    /**
     * 处理truncate。
     */
    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private boolean isEmailTool(AgentTool tool) {
        return tool != null && "send_email".equals(StringUtils.defaultIfBlank(tool.getMcpToolName(), tool.getName()));
    }

    /** 邮件连接信息只允许由服务端注入，模型参数中即使出现也必须丢弃。 */
    private ToolCall withEmailDefaults(AgentTool tool, ToolCall call, String userId) {
        if (!isEmailTool(tool) || call == null) return call;
        Map<String, Object> arguments = new LinkedHashMap<>(call.getArguments() == null
                ? java.util.Collections.<String, Object>emptyMap() : call.getArguments());
        arguments.remove("credential_ref");
        arguments.remove("smtp_host");
        arguments.remove("smtp_port");
        arguments.remove("security");
        return new ToolCall(call.getId(), call.getName(), arguments);
    }

    /**
     * 发件邮箱配置由服务端注入；模型只能看到邮件业务字段，避免要求它猜测或回显 SMTP 连接信息。
     */
    private AgentTool toModelVisibleTool(AgentTool tool) {
        if (!isEmailTool(tool) || StringUtils.isBlank(tool.getMcpInputSchema())) return tool;
        try {
            AgentTool visible = new AgentTool();
            BeanUtils.copyProperties(tool, visible);
            JSONObject schema = JSONObject.parseObject(tool.getMcpInputSchema());
            JSONObject properties = schema.getJSONObject("properties");
            if (properties != null) {
                properties.remove("credential_ref");
                properties.remove("smtp_host");
                properties.remove("smtp_port");
                properties.remove("security");
            }
            JSONArray required = schema.getJSONArray("required");
            if (required != null) {
                required.remove("credential_ref");
                required.remove("smtp_host");
                required.remove("smtp_port");
                required.remove("security");
            }
            visible.setMcpInputSchema(schema.toJSONString());
            visible.setDescription(StringUtils.defaultString(tool.getDescription())
                    + " 发件邮箱与 SMTP 连接配置由平台从当前用户已验证的邮箱配置中注入；不得索取、传入或展示授权码。");
            return visible;
        } catch (Exception e) {
            log.warn("构建邮件工具的模型可见参数失败，保留原始 Schema: toolId={}", tool.getId());
            return tool;
        }
    }

    private String redactSecrets(String value) {
        return value == null ? null : value.replaceAll("(?i)(smtp_authorization_code|password|passwd|credential|token|secret)\\\"?\\s*[:=]\\s*\\\"?[^,}\\s\\\"]+", "$1=***");
    }

    /**
     * 用户确认后的执行结果，供聊天服务继续构造下一轮模型上下文。
     */
    public static class ApprovalExecution {
        private final String runId;
        private final ModelChatResponse toolCallResponse;
        private final ToolExecutionResult result;

        /**
         * 创建 {@code ApprovalExecution} 实例。
         */
        public ApprovalExecution(String runId, ModelChatResponse toolCallResponse, ToolExecutionResult result) {
            this.runId = runId;
            this.toolCallResponse = toolCallResponse;
            this.result = result;
        }

        /**
         * 获取运行Id。
         */
        public String getRunId() {
            return runId;
        }

        /**
         * 获取ToolCallResponse。
         */
        public ModelChatResponse getToolCallResponse() {
            return toolCallResponse;
        }

        /**
         * 获取结果。
         */
        public ToolExecutionResult getResult() {
            return result;
        }
    }
}
