package com.aether.agent.executor.impl;

import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.executor.ToolExecutionContext;
import com.aether.agent.executor.ToolExecutionResult;
import com.aether.agent.service.AgentRunService;
import com.aether.i18n.I18nUtils;
import com.aether.utils.TimeUtils;
import com.aether.workflow.service.AgentWorkflowInvocationService;
import com.aether.workflow.service.AgentWorkflowCapabilityService;
import com.aether.workflow.service.AgentWorkflowTaskQueryService;
import com.aether.workflow.dto.AgentWorkflowTaskListOptions;
import com.aether.workflow.entity.AgentWorkflowCapability;
import com.aether.workflow.vo.AgentWorkflowInvocationObservation;
import com.aether.workflow.vo.AgentWorkflowTaskPage;
import com.aether.workflow.vo.AgentWorkflowTaskVo;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 执行 Agent 绑定的工作流能力工具。 */
@Component
public class WorkflowToolExecutor implements com.aether.agent.executor.ToolExecutor {
    /** 清单要进模型上下文，必须自带上限。 */
    private static final int LIST_LIMIT = 20;
    /** 单字段截断上限，与 ToolResultContextCompressor.isLargeTextField 取同一个值。 */
    private static final int FIELD_MAX_CHARS = 1200;
    /** 目标解析最多看几条在跑的工作流；与清单同一口径，避免两条读路径给出不同答案。 */
    private static final int TARGET_CANDIDATE_LIMIT = LIST_LIMIT;
    /**
     * 只能作用于特定状态的动作。这类动作允许先用 nextAction 收窄候选：只有一条在等它时，
     * 模型不必再从候选里挑一次。
     *
     * <p>OBSERVE 与 STOP 不在此列 —— 它们对任何未终态的调用都成立，按状态收窄会误判。
     */
    private static final List<String> STATE_BOUND_ACTIONS = Arrays.asList(
            "PROVIDE_AGENT_INPUT", "RESOLVE_MCP_APPROVAL", "SIGNAL_EVENT", "RETRY_NODE");

    /**
     * 整个清单载荷的字符预算。
     *
     * <p>必须整体低于 {@code ToolResultContextCompressor.MAX_CONTEXT_CHARS}（6000）：一旦超过，
     * 压缩器会按自己的规则重压一遍 —— 数组只留 10 项、超长字段改截 800 —— 于是模型看到的
     * 形状与我们返回的完全不同。留 400 字符余量给信封。
     */
    private static final int LIST_PAYLOAD_MAX_CHARS = 5600;

    private final AgentWorkflowInvocationService invocationService;
    private final AgentRunService agentRunService;
    private final AgentWorkflowCapabilityService capabilityService;
    private final AgentWorkflowTaskQueryService taskQueryService;

    @Autowired
    public WorkflowToolExecutor(@Lazy AgentWorkflowInvocationService invocationService, AgentRunService agentRunService,
                                @Lazy AgentWorkflowCapabilityService capabilityService,
                                @Lazy AgentWorkflowTaskQueryService taskQueryService) {
        this.invocationService = invocationService;
        this.agentRunService = agentRunService;
        this.capabilityService = capabilityService;
        this.taskQueryService = taskQueryService;
    }

    /** Compatibility constructor for direct unit tests and legacy embedders. */
    public WorkflowToolExecutor(@Lazy AgentWorkflowInvocationService invocationService, AgentRunService agentRunService,
                                @Lazy AgentWorkflowCapabilityService capabilityService) {
        this(invocationService, agentRunService, capabilityService, null);
    }

    /** Compatibility constructor for direct unit tests and legacy embedders. */
    public WorkflowToolExecutor(@Lazy AgentWorkflowInvocationService invocationService, AgentRunService agentRunService) {
        this(invocationService, agentRunService, null, null);
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
        // 解析出的目标要活到 catch：错误载荷里带的必须是服务端实际作用的那个 id，而不是模型
        // 有没有传 —— 否则「状态冲突」的补偿观察会因为 id 为空而整段跳过，模型失去那个闭环。
        String resolvedInvocationId = null;
        Long resolvedStateVersion = null;
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
            // 清单查询不带 invocationId；必须放在目标解析之前。
            if ("LIST".equalsIgnoreCase(action)) {
                return list(context, started, requestArguments);
            }
            // 动作专用工具不再要求模型重复传 action；保留 INSTANCE 的 action
            // 读取逻辑仅用于历史调用和非模型内部调用。
            String requested = StringUtils.defaultIfBlank(string(arguments.get("action")),
                    StringUtils.defaultIfBlank(tool.getWorkflowToolAction(), "OBSERVE")).toUpperCase();
            // 先查：模型不传 invocationId 时，由服务端从会话上下文把目标定下来。
            String invocationId = blankToNull(string(arguments.get("invocationId")));
            if (invocationId == null) {
                TargetResolution resolution = resolveTarget(context, requested, run);
                if (resolution.getInvocationId() == null) {
                    return targetFailure(resolution, requested, requestArguments);
                }
                invocationId = resolution.getInvocationId();
            }
            resolvedInvocationId = invocationId;
            // 后改：模型不再需要先 observe 再回传状态版本。缺省时读当前值即可 —— 领域层的
            // 乐观锁校验原样保留（调用期间真被别处改过仍会 409），只是把「读」从模型的一次
            // 往返挪进了服务端。显式传版本的老调用方（历史脚本、非模型内部调用）行为不变。
            Long expectedStateVersion = longValue(arguments.get("expectedStateVersion"));
            if (expectedStateVersion == null) {
                expectedStateVersion = invocationService.currentStateVersion(
                        invocationId, context.getUserId(), context.getAgentDefinitionId());
            }
            resolvedStateVersion = expectedStateVersion;
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
            // 优先用服务端解析出来的目标：模型没传 invocationId 时，failedArguments 里也是空的。
            return structuredFailure(e, requestedAction(failedArguments, tool),
                    resolvedInvocationId != null ? resolvedInvocationId : string(failedArguments.get("invocationId")),
                    resolvedStateVersion != null ? resolvedStateVersion : longValue(failedArguments.get("expectedStateVersion")),
                    context, failedArguments);
        }
    }

    /**
     * 本 Agent 为当前用户启动的工作流调用：正在处理的在前，最近已结束的在后。
     *
     * <p>返回结构刻意对齐 {@code workflow_observe}，模型无需学习新形状：先算状态、再提交动作。
     * 空清单是有效答案而非错误 —— 模型据此可以停止等待。
     *
     * <p>已结束的调用默认也列出来：它们从清单里消失后，模型会把「已经做完了」读成
     * 「不存在了」，进而向用户编造失败结论。
     *
     * <p>带筛选参数时改走参数化查询，见 {@link #applyQueryArguments}。
     */
    private ToolExecutionResult list(ToolExecutionContext context, long started,
                                     Map<String, Object> requestArguments) {
        AgentWorkflowTaskListOptions options = new AgentWorkflowTaskListOptions();
        options.setInFlightLimit(LIST_LIMIT);
        options.setIncludeTerminal(includeCompleted(context));
        // 精选视图下「是否含已结束」同时决定要不要解析结果；拆出 includeOutput 后这里降级成默认值。
        options.setWithResult(options.isIncludeTerminal());
        try {
            applyQueryArguments(context, options);
        } catch (IllegalArgumentException e) {
            return structuredFailure(e, "LIST", null, null, context, requestArguments);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        List<Map<String, Object>> invocations = new ArrayList<>();
        long total = 0;
        boolean truncated = false;
        if (taskQueryService != null) {
            AgentWorkflowTaskPage page = taskQueryService.listInFlight(context.getAgentDefinitionId(),
                    context.getUserId(), options);
            for (AgentWorkflowTaskVo task : page.getTasks()) invocations.add(invocationView(task));
            total = page.getTotal();
            truncated = page.isTruncated();
        }
        boolean outputOmitted = false;
        if (JSON.toJSONString(invocations).length() > LIST_PAYLOAD_MAX_CHARS) {
            // 字段级截断之后仍然超预算（多条大结果叠加），就整体放弃内联结果，
            // 只留结果码与指引 —— 让压缩器插手会把返回形状整个换掉。
            for (Map<String, Object> item : invocations)
                if (item.remove("output") != null) item.remove("outputTruncated");
            outputOmitted = true;
        }
        payload.put("invocations", invocations);
        payload.put("returned", invocations.size());
        payload.put("total", total);
        payload.put("truncated", truncated);
        if (outputOmitted || anyOutputTruncated(invocations)) {
            if (outputOmitted) payload.put("outputOmitted", true);
            // 措辞刻意不承诺 observe 能拿到全文：它自己的载荷超长时同样会被压缩器
            // 按首尾硬切，插在 JSON 中间 —— 那时模型拿到的是残缺的 JSON。
            payload.put("hint", outputOmitted
                    ? "结果过大，已整体省略内联输出；需要时用 workflow_observe 看单个调用"
                    + "（它自身超长时同样会被压缩）。"
                    : "结果已按字段截断（单字段上限 " + FIELD_MAX_CHARS + " 字符）；"
                    + "workflow_observe 能看到更多，但它自身超长时同样会被压缩。");
        }
        return success(JSON.toJSONString(payload), started, requestArguments);
    }

    /**
     * 把模型给的筛选参数搬进取数选项。
     *
     * <p>时间参数名刻意叫 {@code createdAfter}/{@code createdBefore} 而不是 {@code startTime}/{@code endTime}：
     * 筛的是 {@code createdAt} 这一列，不是工作流的 {@code startedAt}/{@code completedAt}。名字若含糊，
     * 模型问「上周完成的」会发 {@code endTime}，于是我们静默返回一批创建于上周、但至今还在跑的行 ——
     * 不报错、只是答错。工具声明的 schema 带 {@code additionalProperties: false}，参数名就是硬契约，
     * 只能靠名字和 description 消歧。
     *
     * <p>解析失败只抛 {@link IllegalArgumentException}，由 {@link #list} 转成 422 结构化结果。
     * 若放任它冒泡到 {@link #execute} 的兜底分支，模型会收到 500 + {@code WORKFLOW_ACTION_FAILED}，
     * 把「参数写错了」误判成「服务端坏了」，然后原样重试。
     */
    private void applyQueryArguments(ToolExecutionContext context, AgentWorkflowTaskListOptions options) {
        Map<String, Object> arguments = context == null || context.getArguments() == null
                ? java.util.Collections.<String, Object>emptyMap() : context.getArguments();
        options.setInvocationId(blankToNull(string(arguments.get("invocationId"))));
        // state 只在查询模式下才被取数逻辑读到，所以这里不必和 includeCompleted 互斥；
        // 两者同时传时 state 赢，是因为它把整条路径换成了 SQL 查询。
        options.setState(blankToNull(string(arguments.get("state"))));
        options.setCreatedAfter(parseTime(arguments.get("createdAfter"), "createdAfter"));
        options.setCreatedBefore(parseTime(arguments.get("createdBefore"), "createdBefore"));
        options.setCurrent(intValue(arguments.get("current")));
        options.setPageSize(intValue(arguments.get("pageSize")));
        String capabilityCode = blankToNull(string(arguments.get("capabilityCode")));
        if (capabilityCode != null) options.setCapabilityId(resolveCapabilityIdByCode(context, capabilityCode));
        Object includeOutput = arguments.get("includeOutput");
        // 不传时保留上游算出的默认值（= includeCompleted），显式传才覆盖。
        if (includeOutput != null) options.setWithResult(!"false".equalsIgnoreCase(String.valueOf(includeOutput)));
    }

    /**
     * 时间参数双收：ISO-8601（须带 {@code Z} 或偏移）与 ≥12 位的 epoch 毫秒串。
     *
     * <p>解析不了必须报错，不能当「没传」放过去 —— 静默忽略会让模型拿到一份没筛过的清单，
     * 却笃定自己问的是「上周的调用」，随后基于错误事实编出结论。
     */
    private Long parseTime(Object value, String field) {
        String text = blankToNull(string(value));
        if (text == null) return null;
        Long parsed = TimeUtils.parseEpochMillis(text);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid workflow_list argument: " + field
                    + " must be ISO-8601 with offset (2026-09-15T10:00:00Z) or epoch millis, but was: " + text);
        }
        return parsed;
    }

    /** 页码类参数；解析不了按「没传」处理，越界由 service 统一 clamp。 */
    private Integer intValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.valueOf(String.valueOf(value).trim()); }
        catch (NumberFormatException ignored) { return null; }
    }

    /**
     * 把模型给的 {@code capabilityCode} 解析成本 Agent 已绑定能力的主键。
     *
     * <p>刻意不校验 {@code allowedActions}：这里只是把清单缩小到某个能力，不是请求执行动作，
     * 能否 START 由 {@link #resolveStartCapabilityId} 单独把关。
     */
    private String resolveCapabilityIdByCode(ToolExecutionContext context, String capabilityCode) {
        if (capabilityService != null) {
            List<AgentWorkflowCapability> capabilities = capabilityService.listEnabledForAgent(
                    context.getAgentDefinitionId(), context.getApplicationId());
            if (capabilities != null) {
                for (AgentWorkflowCapability capability : capabilities) {
                    if (StringUtils.equals(capabilityCode, capability.getCapabilityCode())) {
                        return capability.getId();
                    }
                }
            }
        }
        // 措辞与 resolveStartCapabilityId 保持一致，好让 structuredFailure 映射到同一个错误码。
        throw new IllegalArgumentException("Workflow capability is not bound to agent: " + capabilityCode);
    }

    private boolean anyOutputTruncated(List<Map<String, Object>> invocations) {
        for (Map<String, Object> item : invocations)
            if (Boolean.TRUE.equals(item.get("outputTruncated"))) return true;
        return false;
    }

    /** 清单里已结束的调用是否连结果一起内联。默认开，显式传 false 可关掉。 */
    private boolean includeCompleted(ToolExecutionContext context) {
        Object value = context == null || context.getArguments() == null
                ? null : context.getArguments().get("includeCompleted");
        return !"false".equalsIgnoreCase(String.valueOf(value));
    }

    private Map<String, Object> invocationView(AgentWorkflowTaskVo task) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("invocationId", task.getInvocationId());
        item.put("instanceId", task.getInstanceId());
        item.put("workflowId", task.getWorkflowId());
        item.put("workflowName", task.getWorkflowName());
        item.put("capabilityCode", task.getCapabilityCode());
        item.put("status", task.getStatus());
        item.put("stateVersion", task.getStateVersion());
        item.put("currentNodeId", task.getCurrentNodeId());
        item.put("currentNodeType", task.getCurrentNodeType());
        item.put("currentNodeName", task.getCurrentNodeName());
        item.put("nextAction", task.getNextAction());
        // 时间是清单里唯一无法从其它字段推出来的信息，给 ISO-8601 而不是裸毫秒：
        // 模型拿着毫秒既算不出「多久之前」，也没法直接跟 createdAfter/createdBefore 比对。
        item.put("startedAt", TimeUtils.formatIsoMillis(task.getStartedAt()));
        item.put("completedAt", TimeUtils.formatIsoMillis(task.getCompletedAt()));
        if (StringUtils.isNotBlank(task.getResultCode())) item.put("resultCode", task.getResultCode());
        appendOutput(item, task.getOutput());
        return item;
    }

    /**
     * 把业务结果裁到字段预算内再内联。
     *
     * <p>截断做在 agent 工具这一层而不是共用的 VO：截断是「模型上下文预算」的关切，
     * 聊天页读的是同一条数据，不该被模型的预算连累。
     *
     * <p>只裁字符串字段；结构化字段原样留着，超预算由 {@link #list} 的整体兜底处理。
     */
    private void appendOutput(Map<String, Object> item, Map<String, Object> output) {
        if (output == null || output.isEmpty()) return;
        Map<String, Object> capped = new LinkedHashMap<>();
        boolean cut = false;
        for (Map.Entry<String, Object> entry : output.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String && ((String) value).length() > FIELD_MAX_CHARS) {
                value = ((String) value).substring(0, FIELD_MAX_CHARS) + "…";
                cut = true;
            }
            capped.put(entry.getKey(), value);
        }
        item.put("output", capped);
        if (cut) item.put("outputTruncated", true);
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

    /**
     * 模型没传 invocationId 时替它定目标 —— 这就是「先查后改」里的「先查」。
     *
     * <p>取数走 {@link AgentWorkflowTaskQueryService}，与聊天页的任务清单同源：会话内已进入终态的
     * 调用不会进候选，由实例真值判定的终态也算数，两条读路径不会给出不同结论。
     *
     * <p>不猜：恰好一条才直接用；零条或多条都返回结构化结果（前者说明没有可操作的调用，后者把
     * 候选原样交给模型）。停错工作流的代价远大于多一次调用，所以这里宁可多问一句。
     */
    private TargetResolution resolveTarget(ToolExecutionContext context, String action, AgentRun run) {
        if (taskQueryService == null) return TargetResolution.required();
        AgentWorkflowTaskListOptions options = new AgentWorkflowTaskListOptions();
        options.setInFlightLimit(TARGET_CANDIDATE_LIMIT);
        options.setIncludeTerminal(false);
        options.setWithResult(false);
        AgentWorkflowTaskPage page;
        try {
            // run 由调用方传入，别在这里再查一次：它是同一个 run，多一次查询纯属浪费。
            String conversationId = run == null ? null : run.getConversationId();
            page = StringUtils.isNotBlank(conversationId)
                    ? taskQueryService.listByConversation(conversationId, null, options)
                    // 会话缺失（评测、脚本调用）时退回「本 Agent 为本用户启动的」：范围更宽，
                    // 但归属校验在领域层照旧兜底，不会因此越权。
                    : taskQueryService.listInFlight(context.getAgentDefinitionId(), context.getUserId(), options);
        } catch (Exception e) {
            // 解析失败按「无法定目标」处理，让模型显式传 invocationId，而不是把异常当领域错误报出去。
            return TargetResolution.required();
        }
        List<AgentWorkflowTaskVo> tasks = page == null ? null : page.getTasks();
        if (tasks == null || tasks.isEmpty()) return TargetResolution.notFound();
        List<AgentWorkflowTaskVo> pool = tasks;
        if (STATE_BOUND_ACTIONS.contains(action)) {
            List<AgentWorkflowTaskVo> waiting = new ArrayList<>();
            for (AgentWorkflowTaskVo task : tasks) {
                if (action.equals(nextActionType(task))) waiting.add(task);
            }
            // 一条都不在等这个动作时不要退回全集：随便挑一条必然被领域层的状态前置条件打回，
            // 模型只会收到一条看不出所以然的状态错误。
            if (waiting.isEmpty()) return TargetResolution.notWaiting(tasks);
            pool = waiting;
        }
        if (pool.size() == 1 && StringUtils.isNotBlank(pool.get(0).getInvocationId())) {
            return TargetResolution.resolved(pool.get(0).getInvocationId());
        }
        return TargetResolution.ambiguous(pool);
    }

    /** {@code nextAction.type} 与动作名同源（见 AgentWorkflowNextActionResolver），可直接比。 */
    private String nextActionType(AgentWorkflowTaskVo task) {
        Map<String, Object> nextAction = task == null ? null : task.getNextAction();
        Object type = nextAction == null ? null : nextAction.get("type");
        return type == null ? null : String.valueOf(type);
    }

    /**
     * 目标没定下来时的结构化结果。
     *
     * <p>候选刻意复用 {@link #invocationView}：与 workflow_list 同一形状，模型不必学第二套。
     */
    private ToolExecutionResult targetFailure(TargetResolution resolution, String action,
                                              Map<String, Object> requestArguments) {
        JSONObject payload = new JSONObject();
        payload.put("success", false);
        payload.put("errorCode", resolution.getErrorCode());
        payload.put("message", resolution.getMessage());
        payload.put("action", action);
        // 目标没定下来 = 模型这次参数不足以成事，retryable 保持 false，逼它换参数而不是原样重试。
        payload.put("retryable", false);
        if (resolution.getRequiredAction() != null) payload.put("requiredAction", resolution.getRequiredAction());
        if (resolution.getCandidates() != null && !resolution.getCandidates().isEmpty()) {
            List<Map<String, Object>> candidates = new ArrayList<>();
            for (AgentWorkflowTaskVo task : resolution.getCandidates()) candidates.add(invocationView(task));
            payload.put("candidates", candidates);
        }
        ToolExecutionResult result = ToolExecutionResult.failure(resolution.getMessage(), 1);
        result.setContent(payload.toJSONString());
        result.setRawResponse(payload.toJSONString());
        result.setHttpStatus(409);
        result.setRequestMethod("WORKFLOW");
        result.setRequestBody(JSON.toJSONString(requestArguments == null
                ? java.util.Collections.<String, Object>emptyMap() : requestArguments));
        return result;
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
        } else if (message.contains("Invalid workflow_list argument")) {
            // 422 而不是 500：这是模型自己把参数写错了。报 500 会让它以为服务端故障，
            // 于是原样重试同一个坏参数；retryable 保持 false，逼它改参数。
            code = "WORKFLOW_LIST_INVALID_ARGUMENT";
            httpStatus = 422;
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

    /** 模型会发 {@code ""}；空串必须等同「没传」，否则 {@code isQueryRequested()} 会被一个空串切进查询模式。 */
    private String blankToNull(String value) { return StringUtils.isBlank(value) ? null : value.trim(); }

    private Long longValue(Object value) {
        if (value == null) return null;
        try { return Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    /**
     * 目标解析的结论：成功只有 {@code invocationId}，失败则由错误码说明原因并附候选。
     *
     * <p>{@code errorCode} 一律是模型可据此换动作的取值，不复用
     * {@code structuredFailure} 的文本匹配 —— 那里是靠英文错误文本反推码的，新加的
     * 分支越多越容易撞车。
     */
    private static final class TargetResolution {
        private final String invocationId;
        private final String errorCode;
        private final String message;
        private final String requiredAction;
        private final List<AgentWorkflowTaskVo> candidates;

        private TargetResolution(String invocationId, String errorCode, String message,
                                 String requiredAction, List<AgentWorkflowTaskVo> candidates) {
            this.invocationId = invocationId;
            this.errorCode = errorCode;
            this.message = message;
            this.requiredAction = requiredAction;
            this.candidates = candidates;
        }

        static TargetResolution resolved(String invocationId) {
            return new TargetResolution(invocationId, null, null, null, null);
        }

        /** 取不到会话上下文（评测、脚本调用）时的兜底：维持「调用方必须显式给 id」的老行为。 */
        static TargetResolution required() {
            return new TargetResolution(null, "WORKFLOW_TARGET_REQUIRED",
                    "Workflow invocationId is required", "LIST", null);
        }

        static TargetResolution notFound() {
            return new TargetResolution(null, "WORKFLOW_TARGET_NOT_FOUND",
                    "No active workflow invocation in this conversation", null, null);
        }

        static TargetResolution notWaiting(List<AgentWorkflowTaskVo> tasks) {
            return new TargetResolution(null, "WORKFLOW_TARGET_NOT_WAITING",
                    "No active workflow invocation is waiting for this action", "OBSERVE", tasks);
        }

        static TargetResolution ambiguous(List<AgentWorkflowTaskVo> tasks) {
            return new TargetResolution(null, "WORKFLOW_TARGET_AMBIGUOUS",
                    "Multiple active workflow invocations: pass invocationId to choose one", "LIST", tasks);
        }

        String getInvocationId() { return invocationId; }
        String getErrorCode() { return errorCode; }
        String getMessage() { return message; }
        String getRequiredAction() { return requiredAction; }
        List<AgentWorkflowTaskVo> getCandidates() { return candidates; }
    }
}
