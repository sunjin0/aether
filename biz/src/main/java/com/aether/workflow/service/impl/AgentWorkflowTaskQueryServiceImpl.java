package com.aether.workflow.service.impl;

import com.aether.agent.entity.AgentRun;
import com.aether.agent.service.AgentRunService;
import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.entity.AgentWorkflowCapability;
import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.entity.AgentWorkflowInvocation;
import com.aether.workflow.entity.AgentWorkflowNodeInstance;
import com.aether.workflow.entity.AgentWorkflowVersion;
import com.aether.workflow.dto.AgentWorkflowTaskListOptions;
import com.aether.workflow.runtime.AgentWorkflowNextActionResolver;
import com.aether.workflow.runtime.WorkflowOutputResolver;
import com.aether.workflow.service.AgentWorkflowCapabilityService;
import com.aether.workflow.service.AgentWorkflowInstanceService;
import com.aether.workflow.service.AgentWorkflowInvocationRecordService;
import com.aether.workflow.service.AgentWorkflowNodeInstanceService;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.workflow.service.AgentWorkflowTaskQueryService;
import com.aether.workflow.service.AgentWorkflowVersionService;
import com.aether.workflow.vo.AgentWorkflowInstanceVo;
import com.aether.workflow.vo.AgentWorkflowTaskPage;
import com.aether.workflow.vo.AgentWorkflowTaskVo;
import com.aether.workflow.vo.AgentWorkflowToolOccupant;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作流任务清单查询。
 *
 * <p>一条铁律：<b>用 {@code agent_workflow_invocation.status} 做廉价候选筛选，以
 * {@code agent_workflow_instance.status} 为展示真值</b>。invocation 的状态只由 observe
 * 和一个「只扫最旧 200 条」的后台处理器回写，超出窗口的调用会永远停在 RUNNING；
 * 若照它展示，agent 会盯着一个早已结束的幻影工作流反复观察。
 *
 * <p>查询次数与行数无关：实例/节点/版本/能力一律按 id 批量取。
 */
@Service
public class AgentWorkflowTaskQueryServiceImpl implements AgentWorkflowTaskQueryService {
    private static final List<String> TERMINAL_STATUSES =
            Arrays.asList("COMPLETED", "FAILED", "TERMINATED", "TIMED_OUT");
    /**
     * 「工作流实例已终态」的子查询，供状态筛选使用。
     *
     * <p>必须自带 {@code deleted = false}：这是裸 SQL，绕过了 {@code @TableLogic} 在其它查询上
     * 自动追加的逻辑删除条件，漏掉它会把已删除的实例也算进来。
     */
    private static final String INSTANCE_TERMINAL_SQL =
            "SELECT id FROM agent_workflow_instance WHERE deleted = false AND status IN "
                    + "('COMPLETED', 'FAILED', 'TERMINATED', 'TIMED_OUT')";
    private static final int CANDIDATE_FACTOR = 5;
    private static final int MAX_CANDIDATES = 200;
    /** 单会话参与筛选的运行数上限；超出时按最近优先截断并置 truncated。 */
    private static final int MAX_RUNS_PER_CONVERSATION = 200;
    /** 工具占用检测最多看几条在跑的工作流；护栏宁可少拦，也不能拖慢每次工具调用。 */
    private static final int OCCUPANT_CANDIDATES = 20;

    private final AgentWorkflowInvocationRecordService invocationStore;
    private final AgentWorkflowInstanceService instanceService;
    private final AgentWorkflowNodeInstanceService nodeService;
    private final AgentWorkflowVersionService versionService;
    private final AgentWorkflowCapabilityService capabilityService;
    private final AgentWorkflowService workflowService;
    private final AgentRunService runService;
    private final AgentWorkflowNextActionResolver nextActionResolver;
    private final WorkflowOutputResolver outputResolver;

    /** 兼容旧的装配方式：不解析结果时用不到输出契约，允许为空。 */
    public AgentWorkflowTaskQueryServiceImpl(AgentWorkflowInvocationRecordService invocationStore,
                                             AgentWorkflowInstanceService instanceService,
                                             AgentWorkflowNodeInstanceService nodeService,
                                             AgentWorkflowVersionService versionService,
                                             AgentWorkflowCapabilityService capabilityService,
                                             AgentWorkflowService workflowService,
                                             AgentRunService runService,
                                             AgentWorkflowNextActionResolver nextActionResolver) {
        this(invocationStore, instanceService, nodeService, versionService, capabilityService,
                workflowService, runService, nextActionResolver, null);
    }

    @Autowired
    public AgentWorkflowTaskQueryServiceImpl(AgentWorkflowInvocationRecordService invocationStore,
                                             AgentWorkflowInstanceService instanceService,
                                             AgentWorkflowNodeInstanceService nodeService,
                                             AgentWorkflowVersionService versionService,
                                             AgentWorkflowCapabilityService capabilityService,
                                             AgentWorkflowService workflowService,
                                             AgentRunService runService,
                                             AgentWorkflowNextActionResolver nextActionResolver,
                                             WorkflowOutputResolver outputResolver) {
        this.invocationStore = invocationStore;
        this.instanceService = instanceService;
        this.nodeService = nodeService;
        this.versionService = versionService;
        this.capabilityService = capabilityService;
        this.workflowService = workflowService;
        this.runService = runService;
        this.nextActionResolver = nextActionResolver;
        this.outputResolver = outputResolver;
    }

    /**
     * 把实例真值翻译成展示状态。
     *
     * <p>等待用户输入与等待 MCP 授权在 {@code agent_workflow_invocation.status} 里都只是
     * 同一个 {@code WAITING_ACTION}，只能靠下一步动作区分。
     *
     * @param rawStatus      实例状态（无实例行时为 invocation 的投影状态）
     * @param nextActionType workflow_observe 风格的下一步动作类型
     */
    public static String displayStatus(String rawStatus, String nextActionType) {
        if (TERMINAL_STATUSES.contains(rawStatus)) return rawStatus;
        if ("RESOLVE_MCP_APPROVAL".equals(nextActionType)) return "WAITING_MCP_APPROVAL";
        if ("PROVIDE_AGENT_INPUT".equals(nextActionType) || "WAITING_HUMAN".equals(nextActionType))
            return "WAITING_USER_INPUT";
        // WAITING_EVENT / WAITING_SUBFLOW 等的是外部系统而不是人，统一按运行中呈现。
        return "RUNNING";
    }

    @Override
    public AgentWorkflowTaskPage listInFlight(String agentDefinitionId, String principalId, int limit) {
        AgentWorkflowTaskListOptions options = new AgentWorkflowTaskListOptions();
        options.setInFlightLimit(limit);
        // 旧签名的语义就是「只看没结束的」。保留 terminalLimit=0 既守住既有调用方的行为，
        // 也让「全终态时一次多余查询都不发」这条优化原样成立。
        options.setTerminalLimit(0);
        return listInFlight(agentDefinitionId, principalId, options);
    }

    /**
     * 清单取数的唯一入口，内部分两种结构：
     *
     * <p>没有筛选参数时走两段式精选视图（在跑取满、已结束补位），语义与历史和调用方一致；
     * 一旦带了筛选参数就切换成参数化查询 —— 只由<b>筛选</b>参数触发，页码与开关不算，
     * 理由见 {@link AgentWorkflowTaskListOptions#isQueryRequested()}。
     */
    @Override
    public AgentWorkflowTaskPage listInFlight(String agentDefinitionId, String principalId,
                                              AgentWorkflowTaskListOptions options) {
        if (StringUtils.isAnyBlank(agentDefinitionId, principalId)) return new AgentWorkflowTaskPage();
        AgentWorkflowTaskListOptions effective = options == null ? new AgentWorkflowTaskListOptions() : options;
        return effective.isQueryRequested()
                ? queryPage(agentDefinitionId, principalId, effective)
                : curatedPage(agentDefinitionId, principalId, effective);
    }

    /**
     * 两段式精选视图：在跑的在前、最近已结束的补位。
     *
     * <p>全部筛选参数为 null 时行为与引入参数化查询之前逐字节一致 —— 既有测试原样钉着这条。
     */
    private AgentWorkflowTaskPage curatedPage(String agentDefinitionId, String principalId,
                                              AgentWorkflowTaskListOptions effective) {
        AgentWorkflowTaskPage page = new AgentWorkflowTaskPage();
        int inFlightLimit = Math.max(0, effective.getInFlightLimit());
        int terminalLimit = effective.isIncludeTerminal() ? Math.max(0, effective.getTerminalLimit()) : 0;
        if (inFlightLimit <= 0 && terminalLimit <= 0) return page;

        // 两条候选查询各自按 createdAt 倒序并只看窗口内，再按 id 去重：同一条调用可能
        // 既落在「未终态」的候选里，又被实例真值判成已结束。
        Map<String, AgentWorkflowInvocation> candidates = new LinkedHashMap<>();
        boolean truncated = false;
        if (inFlightLimit > 0) {
            int window = windowOf(inFlightLimit);
            List<AgentWorkflowInvocation> rows = nullSafe(invocationStore.list(
                    candidateQuery(agentDefinitionId, principalId, window)
                            .notIn(AgentWorkflowInvocation::getStatus, TERMINAL_STATUSES)));
            for (AgentWorkflowInvocation row : rows) candidates.put(row.getId(), row);
            truncated |= rows.size() >= window;
        }
        if (terminalLimit > 0) {
            int window = windowOf(terminalLimit);
            List<AgentWorkflowInvocation> rows = nullSafe(invocationStore.list(
                    candidateQuery(agentDefinitionId, principalId, window)
                            .in(AgentWorkflowInvocation::getStatus, TERMINAL_STATUSES)));
            for (AgentWorkflowInvocation row : rows) candidates.put(row.getId(), row);
            truncated |= rows.size() >= window;
        }

        List<AgentWorkflowInvocation> ordered = new ArrayList<>(candidates.values());
        ordered.sort(Comparator.comparing(AgentWorkflowInvocation::getCreatedAt,
                Comparator.nullsLast(Comparator.<Long>reverseOrder())));

        // 终态行一条都不会被返回时，解析它们的结果是纯浪费 —— 连带省掉版本与能力的查询。
        boolean resolveResults = effective.isWithResult() && terminalLimit > 0;
        // 按实例真值分组。「invocation 行还停在 RUNNING、实例早已终态」的幻影行必须留下 ——
        // 把「刚完成」丢掉会变成「不存在」，那正是模型编造失败结论的来路。
        List<AgentWorkflowTaskVo> running = new ArrayList<>();
        List<AgentWorkflowTaskVo> finished = new ArrayList<>();
        for (AgentWorkflowTaskVo task : collect(ordered, resolveResults)) {
            if (nextActionResolver.isTerminal(task.getRawStatus())) finished.add(task);
            else running.add(task);
        }

        List<AgentWorkflowTaskVo> tasks = new ArrayList<>();
        tasks.addAll(take(running, inFlightLimit));
        tasks.addAll(take(finished, terminalLimit));

        // 页码在两种结构下都生效。精选视图是「先按在跑优先组合好，再切片」，
        // 所以页码永远无法把在跑的行挤到已结束之后。
        // 不传页码时原样返回整个精选视图（≤ inFlightLimit + terminalLimit 条），保持历史语义。
        if (effective.getCurrent() == null && effective.getPageSize() == null) {
            page.setTasks(tasks);
            page.setTotal(tasks.size());
            // 候选窗口被填满说明后面大概率还有；宁可多标一次截断，也不要假装列全了。
            page.setTruncated(truncated);
            return page;
        }
        int pageSize = clampPageSize(effective.getPageSize());
        long offset = offsetOf(effective.getCurrent(), pageSize);
        page.setTasks(slice(tasks, offset, pageSize));
        page.setTotal(tasks.size());
        page.setTruncated(truncated || offset + pageSize < tasks.size());
        return page;
    }

    /**
     * 参数化查询：按筛选条件走单一 SQL，按创建时间倒序翻页。
     *
     * <p>与精选视图的差别只在 WHERE / ORDER / LIMIT。展示投影仍然交给
     * {@link #collect(List, boolean)} 按实例真值装配 —— 这里绝不换状态来源，否则
     * 按 {@code invocationId} 精确查一条会返回幻影的 RUNNING，调用方就会对着一个
     * 早已结束的工作流无限轮询。
     */
    private AgentWorkflowTaskPage queryPage(String agentDefinitionId, String principalId,
                                            AgentWorkflowTaskListOptions effective) {
        AgentWorkflowTaskPage page = new AgentWorkflowTaskPage();
        int pageSize = clampPageSize(effective.getPageSize());
        long offset = offsetOf(effective.getCurrent(), pageSize);

        page.setTotal(invocationStore.count(queryScope(agentDefinitionId, principalId, effective)));
        List<AgentWorkflowInvocation> rows = nullSafe(invocationStore.list(
                queryScope(agentDefinitionId, principalId, effective)
                        .orderByDesc(AgentWorkflowInvocation::getCreatedAt)
                        // created_at 是毫秒，同毫秒的行在 LIMIT/OFFSET 下翻页会重复或漏掉，
                        // 用主键兜底。别再往 .last() 里塞 ORDER BY：两处都发会生成两个 ORDER BY。
                        .orderByDesc(AgentWorkflowInvocation::getId)
                        .last("LIMIT " + pageSize + " OFFSET " + offset)));
        page.setTasks(collect(rows, effective.isWithResult()));
        // 由「本页是否被填满」推出，不能由 total 推 —— 见 AgentWorkflowTaskPage 的类注释。
        page.setTruncated(rows.size() >= pageSize);
        return page;
    }

    /**
     * 参数化查询的筛选条件。
     *
     * <p><b>只放条件，不放 ORDER BY / LIMIT。</b>count 与 list 各调一次拿独立实例：若共用
     * 一个已经带了 {@code LIMIT n OFFSET m} 的 wrapper，PostgreSQL 下
     * {@code SELECT COUNT(*) ... LIMIT 20 OFFSET 20} 的聚合行会被 OFFSET 跳过而返回 0 行，
     * MyBatis-Plus 再把 null 吞成 0 —— 于是第二页起 total 静默变成 0。
     */
    private LambdaQueryWrapper<AgentWorkflowInvocation> queryScope(String agentDefinitionId, String principalId,
                                                                   AgentWorkflowTaskListOptions options) {
        LambdaQueryWrapper<AgentWorkflowInvocation> query = Wrappers.lambdaQuery(AgentWorkflowInvocation.class)
                .eq(AgentWorkflowInvocation::getAgentDefinitionId, agentDefinitionId)
                .eq(AgentWorkflowInvocation::getPrincipalId, principalId);
        if (StringUtils.isNotBlank(options.getInvocationId()))
            query.eq(AgentWorkflowInvocation::getId, options.getInvocationId());
        if (StringUtils.isNotBlank(options.getCapabilityId()))
            query.eq(AgentWorkflowInvocation::getCapabilityId, options.getCapabilityId());
        if (options.getCreatedAfter() != null)
            query.ge(AgentWorkflowInvocation::getCreatedAt, options.getCreatedAfter());
        if (options.getCreatedBefore() != null)
            query.le(AgentWorkflowInvocation::getCreatedAt, options.getCreatedBefore());
        applyStateFilter(query, options.getState());
        return query;
    }

    /**
     * 状态筛选按<b>工作流实例</b>的终态判定，而不是 invocation 行的 status。
     *
     * <p>invocation 的 status 只由 {@code observe} 和一个「永远只扫全表最旧 200 条」的后台
     * 处理器回写（见 AgentWorkflowInvocationOutboxProcessor）。跑完了却没人观察过的调用会永远
     * 停在 RUNNING —— 而那恰恰是用户问「完成了什么」时最想看到的行。照 invocation.status
     * 过滤会让 {@code finished} 精确地漏掉它们，等于用新门重演同一个事故。
     */
    private void applyStateFilter(LambdaQueryWrapper<AgentWorkflowInvocation> query, String state) {
        if (StringUtils.isBlank(state)) return;
        String normalized = state.trim().toLowerCase();
        if ("finished".equals(normalized)) {
            query.inSql(AgentWorkflowInvocation::getWorkflowInstanceId, INSTANCE_TERMINAL_SQL);
        } else if ("running".equals(normalized)) {
            // 取补集。NULL NOT IN (...) 求值为 NULL 会整行丢掉，所以没有实例 ID 的行要显式放行。
            query.and(w -> w.isNull(AgentWorkflowInvocation::getWorkflowInstanceId)
                    .or().notInSql(AgentWorkflowInvocation::getWorkflowInstanceId, INSTANCE_TERMINAL_SQL));
        }
        // 其余取值（含 all）不筛。
    }

    /** 这条路径没有 controller 兜底，越界页码会直接变成非法的 LIMIT/OFFSET。 */
    private static int clampPageSize(Integer pageSize) {
        if (pageSize == null) return AgentWorkflowTaskListOptions.DEFAULT_PAGE_SIZE;
        return Math.max(1, Math.min(AgentWorkflowTaskListOptions.MAX_PAGE_SIZE, pageSize));
    }

    /** 用 long 运算再夹上限：{@code (current - 1) * pageSize} 在 int 下会溢出成负 OFFSET。 */
    private static long offsetOf(Integer current, int pageSize) {
        int page = current == null ? 1 : Math.max(1, current);
        return Math.min(AgentWorkflowTaskListOptions.MAX_OFFSET, ((long) page - 1) * pageSize);
    }

    private static <T> List<T> slice(List<T> values, long offset, int pageSize) {
        if (values.isEmpty() || offset >= values.size()) return Collections.emptyList();
        int from = (int) offset;
        int to = (int) Math.min((long) values.size(), offset + pageSize);
        return new ArrayList<>(values.subList(from, to));
    }

    private LambdaQueryWrapper<AgentWorkflowInvocation> candidateQuery(String agentDefinitionId, String principalId,
                                                                      int window) {
        return Wrappers.lambdaQuery(AgentWorkflowInvocation.class)
                .eq(AgentWorkflowInvocation::getAgentDefinitionId, agentDefinitionId)
                .eq(AgentWorkflowInvocation::getPrincipalId, principalId)
                .orderByDesc(AgentWorkflowInvocation::getCreatedAt)
                .last("LIMIT " + window);
    }

    /** 候选窗口系数：实例真值会把一部分候选判成已结束，多取一些才凑得够 limit。 */
    private static int windowOf(int limit) {
        return Math.min(limit * CANDIDATE_FACTOR, MAX_CANDIDATES);
    }

    private static <T> List<T> take(List<T> values, int limit) {
        if (limit <= 0 || values.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(values.subList(0, Math.min(limit, values.size())));
    }

    @Override
    public AgentWorkflowTaskPage listByConversation(String conversationId, String runId, boolean includeTerminal,
                                                    int current, int pageSize) {
        AgentWorkflowTaskListOptions options = new AgentWorkflowTaskListOptions();
        options.setIncludeTerminal(includeTerminal);
        options.setCurrent(current);
        options.setPageSize(pageSize);
        return listByConversation(conversationId, runId, options);
    }

    /**
     * 会话清单的取数。翻页交给 SQL，展示投影仍由 {@link #collect(List, boolean)} 按实例真值装配。
     *
     * <p>与参数化查询那条路的差别只有作用域 —— 这里按会话下的运行圈定，而不是 agent + principal；
     * 状态筛选、页码 clamp 与排序兜底全部复用同一套实现。
     */
    @Override
    public AgentWorkflowTaskPage listByConversation(String conversationId, String runId,
                                                    AgentWorkflowTaskListOptions options) {
        AgentWorkflowTaskPage page = new AgentWorkflowTaskPage();
        AgentWorkflowTaskListOptions effective = options == null ? new AgentWorkflowTaskListOptions() : options;
        if (StringUtils.isBlank(conversationId)) return page;
        // 非正页码返回空页是旧签名的语义，别让它被 clamp 成 1 条。
        if (effective.getPageSize() != null && effective.getPageSize() <= 0) return page;
        int pageSize = clampPageSize(effective.getPageSize());
        long offset = offsetOf(effective.getCurrent(), pageSize);

        List<String> runIds = runIds(conversationId, runId);
        if (runIds.isEmpty()) return page;

        page.setTotal(invocationStore.count(conversationScope(runIds, effective)));
        List<AgentWorkflowInvocation> rows = nullSafe(invocationStore.list(
                conversationScope(runIds, effective)
                        .orderByDesc(AgentWorkflowInvocation::getCreatedAt)
                        // created_at 是毫秒，同毫秒的行在 LIMIT/OFFSET 下翻页会重复或漏掉，用主键兜底。
                        // 别再往 .last() 里塞 ORDER BY：两处都发会生成两个 ORDER BY。
                        .orderByDesc(AgentWorkflowInvocation::getId)
                        .last("LIMIT " + pageSize + " OFFSET " + offset)));
        page.setTasks(collect(rows, effective.isWithResult()));
        page.setTruncated(runIds.size() >= MAX_RUNS_PER_CONVERSATION);
        return page;
    }

    @Override
    public AgentWorkflowToolOccupant findRunningToolOwner(String agentDefinitionId, String principalId, String toolId) {
        if (StringUtils.isAnyBlank(agentDefinitionId, principalId, toolId)) return null;
        // 热路径的第一道闸：绝大多数 agent 名下没有在跑的工作流，这一次查询为空就结束。
        List<AgentWorkflowInvocation> candidates = invocationStore.list(
                candidateQuery(agentDefinitionId, principalId, OCCUPANT_CANDIDATES)
                        .notIn(AgentWorkflowInvocation::getStatus, TERMINAL_STATUSES));
        if (candidates.isEmpty()) return null;

        Map<String, AgentWorkflowInstance> instances = loadInstances(candidates);
        List<AgentWorkflowInvocation> active = new ArrayList<>();
        for (AgentWorkflowInvocation row : candidates)
            if (!nextActionResolver.isTerminal(statusOf(instances, row))) active.add(row);
        if (active.isEmpty()) return null;

        Map<String, AgentWorkflowNodeInstance> nodes = loadCurrentNodes(active, instances);
        Map<String, AgentWorkflowVersion> versions = loadVersions(active, instances);
        Map<String, AgentWorkflow> workflows = loadWorkflows(active);
        for (AgentWorkflowInvocation row : active) {
            AgentWorkflowInstance instance = instanceOf(instances, row);
            if (instance == null || StringUtils.isBlank(instance.getCurrentNodeId())) continue;
            AgentWorkflowNodeInstance node = nodes.get(nodeKey(instance.getId(), instance.getCurrentNodeId()));
            if (node == null || !"tool".equalsIgnoreCase(node.getNodeType())) continue;
            String rawStatus = statusOf(instances, row);
            AgentWorkflowInstanceVo snapshot = snapshotOf(instance, node,
                    versions.get(versionIdOf(row, instance)), rawStatus);
            JSONObject definition = nextActionResolver.nodeDefinition(snapshot, instance.getCurrentNodeId());
            // 按 resourceId 精确匹配：它和 agent_tool 的主键是同一个 id，不必按名字猜。
            if (definition == null || !StringUtils.equals(toolId, definition.getString("resourceId"))) continue;
            AgentWorkflowToolOccupant occupant = new AgentWorkflowToolOccupant();
            occupant.setInvocationId(row.getId());
            occupant.setWorkflowId(row.getWorkflowId());
            AgentWorkflow workflow = workflows.get(row.getWorkflowId());
            occupant.setWorkflowName(workflow == null ? null : workflow.getName());
            occupant.setNodeId(instance.getCurrentNodeId());
            occupant.setToolName(StringUtils.defaultIfBlank(definition.getString("toolName"),
                    definition.getString("name")));
            return occupant;
        }
        return null;
    }

    private LambdaQueryWrapper<AgentWorkflowInvocation> conversationScope(List<String> runIds,
                                                                          AgentWorkflowTaskListOptions options) {
        LambdaQueryWrapper<AgentWorkflowInvocation> query = Wrappers.lambdaQuery(AgentWorkflowInvocation.class)
                .in(AgentWorkflowInvocation::getAgentRunId, runIds);
        if (StringUtils.isNotBlank(options.getState())) {
            // 与工具侧不同，这里连 'all' 也要认：它表示「终态不过滤」。请求一旦带了 state 就完全
            // 接管 includeTerminal —— 否则界面上选「全部」会在默认的 includeTerminal=false 下
            // 退化成「只看未结束」，两个开关各说一半。
            applyStateFilter(query, options.getState());
        } else if (!options.isIncludeTerminal()) {
            query.notIn(AgentWorkflowInvocation::getStatus, TERMINAL_STATUSES);
        }
        return query;
    }

    /**
     * 会话下的运行 ID。会话通常只有个位数运行，上限只为防病态会话把 IN 子句撑爆。
     *
     * <p>这里用列名而不是 lambda：{@code agent_run} 行里带着 rawResponse 这类大字段，
     * 必须做列投影；而 {@code LambdaQueryWrapper.select(SFunction...)} 会立刻把 lambda
     * 解析成列名，在没有 MyBatis 上下文的单元测试里会抛「can not find lambda cache」。
     */
    private List<String> runIds(String conversationId, String runId) {
        QueryWrapper<AgentRun> query = Wrappers.<AgentRun>query()
                .select("id")
                .eq("conversation_id", conversationId)
                .orderByDesc("created_at")
                .last("LIMIT " + MAX_RUNS_PER_CONVERSATION);
        if (StringUtils.isNotBlank(runId)) query.eq("id", runId);
        List<String> ids = new ArrayList<>();
        for (AgentRun run : nullSafe(runService.list(query))) ids.add(run.getId());
        return ids;
    }

    private List<AgentWorkflowTaskVo> collect(List<AgentWorkflowInvocation> rows) {
        return collect(rows, false);
    }

    /**
     * 批量装配展示行。
     *
     * <p>版本与能力只对「要算下一步动作的行」和「要解析结果的行」加载 —— 终态行没有下一步
     * 动作可算，不取结果时全是终态的清单应当一次多余查询都不发。
     *
     * @param withResult 是否为终态行解析业务结果。解析要读版本的输出契约，是本次装配里
     *                   唯一的额外开销，所以由调用方按需开启。
     */
    private List<AgentWorkflowTaskVo> collect(List<AgentWorkflowInvocation> rows, boolean withResult) {
        List<AgentWorkflowTaskVo> tasks = new ArrayList<>();
        if (rows == null || rows.isEmpty()) return tasks;

        Map<String, AgentWorkflowInstance> instances = loadInstances(rows);
        Map<String, AgentWorkflow> workflows = loadWorkflows(rows);
        List<AgentWorkflowInvocation> active = new ArrayList<>();
        List<AgentWorkflowInvocation> finished = new ArrayList<>();
        for (AgentWorkflowInvocation row : rows) {
            if (nextActionResolver.isTerminal(statusOf(instances, row))) finished.add(row);
            else active.add(row);
        }
        List<AgentWorkflowInvocation> needed = new ArrayList<>(active);
        if (withResult) needed.addAll(finished);
        Map<String, AgentWorkflowNodeInstance> nodes = active.isEmpty()
                ? Collections.<String, AgentWorkflowNodeInstance>emptyMap() : loadCurrentNodes(active, instances);
        Map<String, AgentWorkflowVersion> versions = needed.isEmpty()
                ? Collections.<String, AgentWorkflowVersion>emptyMap() : loadVersions(needed, instances);
        Map<String, AgentWorkflowCapability> capabilities = needed.isEmpty()
                ? Collections.<String, AgentWorkflowCapability>emptyMap() : loadCapabilities(needed);

        for (AgentWorkflowInvocation row : rows) {
            AgentWorkflowInstance instance = instanceOf(instances, row);
            String rawStatus = statusOf(instances, row);
            AgentWorkflowTaskVo task = new AgentWorkflowTaskVo();
            task.setInvocationId(row.getId());
            task.setInstanceId(row.getWorkflowInstanceId());
            task.setWorkflowId(row.getWorkflowId());
            task.setCapabilityId(row.getCapabilityId());
            task.setRawStatus(rawStatus);
            task.setStartedAt(row.getStartedAt());
            task.setCompletedAt(row.getCompletedAt());
            AgentWorkflow workflow = workflows.get(row.getWorkflowId());
            if (workflow != null) task.setWorkflowName(workflow.getName());
            AgentWorkflowCapability capability = capabilities.get(row.getCapabilityId());
            if (capability != null) {
                task.setCapabilityCode(capability.getCapabilityCode());
                task.setCapabilityName(StringUtils.defaultIfBlank(capability.getDisplayName(),
                        capability.getCapabilityCode()));
            }
            if (instance != null) {
                task.setStateVersion(instance.getStateVersion());
                task.setCurrentNodeId(instance.getCurrentNodeId());
            }
            AgentWorkflowNodeInstance node = nodes.get(nodeKey(row.getWorkflowInstanceId(), task.getCurrentNodeId()));
            AgentWorkflowInstanceVo snapshot = snapshotOf(instance, node, versions.get(versionIdOf(row, instance)), rawStatus);
            if (node != null) {
                task.setCurrentNodeType(node.getNodeType());
                task.setCurrentNodeName(nextActionResolver.nodeName(snapshot, node.getNodeId()));
            }
            // 能力可能已被删除或收回，此时按「未授予任何动作」解析，调用本身仍要可见。
            Map<String, Object> nextAction = nextActionResolver.resolve(capability, snapshot);
            task.setNextAction(nextAction);
            task.setStatus(displayStatus(rawStatus, String.valueOf(nextAction.get("type"))));
            if (nextActionResolver.isTerminal(rawStatus)) {
                task.setResultCode(AgentWorkflowNextActionResolver.resultCode(rawStatus));
                if (withResult && outputResolver != null) task.setOutput(outputResolver.resolve(
                        instance, versions.get(versionIdOf(row, instance)), capabilitySchemaOf(capability)));
            }
            tasks.add(task);
        }
        return tasks;
    }

    private String capabilitySchemaOf(AgentWorkflowCapability capability) {
        return capability == null ? null : capability.getOutputSchema();
    }

    private Map<String, AgentWorkflowInstance> loadInstances(List<AgentWorkflowInvocation> rows) {
        Set<String> ids = new LinkedHashSet<>();
        for (AgentWorkflowInvocation row : rows)
            if (StringUtils.isNotBlank(row.getWorkflowInstanceId())) ids.add(row.getWorkflowInstanceId());
        Map<String, AgentWorkflowInstance> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        for (AgentWorkflowInstance instance : nullSafe(instanceService.listByIds(ids)))
            result.put(instance.getId(), instance);
        return result;
    }

    private Map<String, AgentWorkflow> loadWorkflows(List<AgentWorkflowInvocation> rows) {
        Set<String> ids = new LinkedHashSet<>();
        for (AgentWorkflowInvocation row : rows)
            if (StringUtils.isNotBlank(row.getWorkflowId())) ids.add(row.getWorkflowId());
        Map<String, AgentWorkflow> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        for (AgentWorkflow workflow : nullSafe(workflowService.listByIds(ids)))
            result.put(workflow.getId(), workflow);
        return result;
    }

    private Map<String, AgentWorkflowVersion> loadVersions(List<AgentWorkflowInvocation> rows,
                                                           Map<String, AgentWorkflowInstance> instances) {
        Set<String> ids = new LinkedHashSet<>();
        for (AgentWorkflowInvocation row : rows) {
            String versionId = versionIdOf(row, instanceOf(instances, row));
            if (StringUtils.isNotBlank(versionId)) ids.add(versionId);
        }
        Map<String, AgentWorkflowVersion> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        for (AgentWorkflowVersion version : nullSafe(versionService.listByIds(ids)))
            result.put(version.getId(), version);
        return result;
    }

    private Map<String, AgentWorkflowCapability> loadCapabilities(List<AgentWorkflowInvocation> rows) {
        Set<String> ids = new LinkedHashSet<>();
        for (AgentWorkflowInvocation row : rows)
            if (StringUtils.isNotBlank(row.getCapabilityId())) ids.add(row.getCapabilityId());
        Map<String, AgentWorkflowCapability> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        for (AgentWorkflowCapability capability : nullSafe(capabilityService.listByIds(ids)))
            result.put(capability.getId(), capability);
        return result;
    }

    /** 当前节点一次批量取回，避免按行回查（每行一次查询正是 observe 昂贵的根源）。 */
    private Map<String, AgentWorkflowNodeInstance> loadCurrentNodes(List<AgentWorkflowInvocation> rows,
                                                                    Map<String, AgentWorkflowInstance> instances) {
        Set<String> instanceIds = new LinkedHashSet<>();
        Set<String> nodeIds = new LinkedHashSet<>();
        for (AgentWorkflowInvocation row : rows) {
            AgentWorkflowInstance instance = instanceOf(instances, row);
            if (instance == null || StringUtils.isBlank(instance.getCurrentNodeId())) continue;
            instanceIds.add(instance.getId());
            nodeIds.add(instance.getCurrentNodeId());
        }
        Map<String, AgentWorkflowNodeInstance> result = new HashMap<>();
        if (instanceIds.isEmpty()) return result;
        List<AgentWorkflowNodeInstance> found = nodeService.list(
                Wrappers.lambdaQuery(AgentWorkflowNodeInstance.class)
                        .in(AgentWorkflowNodeInstance::getInstanceId, instanceIds)
                        .in(AgentWorkflowNodeInstance::getNodeId, nodeIds));
        for (AgentWorkflowNodeInstance node : nullSafe(found))
            result.put(nodeKey(node.getInstanceId(), node.getNodeId()), node);
        return result;
    }

    private AgentWorkflowInstanceVo snapshotOf(AgentWorkflowInstance instance, AgentWorkflowNodeInstance node,
                                               AgentWorkflowVersion version, String fallbackStatus) {
        AgentWorkflowInstanceVo snapshot = new AgentWorkflowInstanceVo();
        if (instance != null) {
            snapshot.setId(instance.getId());
            snapshot.setCurrentNodeId(instance.getCurrentNodeId());
            snapshot.setStateVersion(instance.getStateVersion());
            snapshot.setWorkflowVersionId(instance.getWorkflowVersionId());
        }
        // start() 失败路径下可能没有实例行，此时用 invocation 的投影状态兜底，
        // 否则 resolver 会把一个已失败的调用当成仍在运行。
        snapshot.setStatus(StringUtils.defaultIfBlank(instance == null ? null : instance.getStatus(), fallbackStatus));
        if (node != null) snapshot.setNodes(Collections.singletonList(node));
        if (version != null) snapshot.setVersionNodes(version.getNodes());
        return snapshot;
    }

    /** 实例的 invocation_id 指向别的调用时不认，避免历史数据串号。 */
    private AgentWorkflowInstance instanceOf(Map<String, AgentWorkflowInstance> instances, AgentWorkflowInvocation row) {
        if (StringUtils.isBlank(row.getWorkflowInstanceId())) return null;
        AgentWorkflowInstance instance = instances.get(row.getWorkflowInstanceId());
        if (instance != null && StringUtils.isNotBlank(instance.getInvocationId())
                && !StringUtils.equals(instance.getInvocationId(), row.getId())) return null;
        return instance;
    }

    private String statusOf(Map<String, AgentWorkflowInstance> instances, AgentWorkflowInvocation row) {
        AgentWorkflowInstance instance = instanceOf(instances, row);
        String status = instance == null ? null : instance.getStatus();
        return StringUtils.defaultIfBlank(status, row.getStatus());
    }

    private String versionIdOf(AgentWorkflowInvocation row, AgentWorkflowInstance instance) {
        return instance == null ? row.getWorkflowVersionId() : instance.getWorkflowVersionId();
    }

    private String nodeKey(String instanceId, String nodeId) {
        return instanceId + "::" + nodeId;
    }

    private static <T> List<T> nullSafe(List<T> values) {
        return values == null ? Collections.<T>emptyList() : values;
    }
}
