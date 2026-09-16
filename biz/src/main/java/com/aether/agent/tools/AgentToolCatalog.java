package com.aether.agent.tools;

import com.aether.agent.entity.AgentTool;
import com.aether.agent.entity.AgentToolBinding;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.AgentToolBindingService;
import com.aether.agent.service.AgentToolService;
import com.aether.agent.tools.core.ToolRegistry;
import com.aether.local.CurrentUser;
import com.aether.workflow.entity.AgentWorkflowCapability;
import com.aether.workflow.service.AgentWorkflowCapabilityService;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 负责查询 Agent 可用工具并维护工具列表缓存。
 *
 * <p>绑定关系、工具启停状态和内置工具的合并均集中在此处，调用方无需了解缓存细节。</p>
 */
@Component
public class AgentToolCatalog {
    private static final Logger log = LoggerFactory.getLogger(AgentToolCatalog.class);
    /**
     * 固定工作流工具协议；版本号用于淘汰旧的缓存。
     *
     * <p>{@code workflowCacheMatches} 只校验「有没有能力绑定」，不校验 schema，所以改动
     * 任何工具的参数或描述都必须提这个版本号，否则最多 10 分钟不生效。
     */
    private static final String CACHE_KEY_PREFIX = "agent:tools:v7:";
    private static final long CACHE_TTL_MINUTES = 10;

    private final AgentToolService agentToolService;
    private final AgentToolBindingService bindingService;
    private final ToolRegistry toolRegistry;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AgentDefinitionService agentDefinitionService;
    private final AgentWorkflowCapabilityService workflowCapabilityService;

    /**
     * 创建 {@code AgentToolCatalog} 实例。
     */
    @Autowired
    public AgentToolCatalog(AgentToolService agentToolService, AgentToolBindingService bindingService,
                            ToolRegistry toolRegistry,
                            @Qualifier("objectRedisTemplate") RedisTemplate<String, Object> redisTemplate,
                            AgentDefinitionService agentDefinitionService,
                            AgentWorkflowCapabilityService workflowCapabilityService) {
        this.agentToolService = agentToolService;
        this.bindingService = bindingService;
        this.toolRegistry = toolRegistry;
        this.redisTemplate = redisTemplate;
        this.agentDefinitionService = agentDefinitionService;
        this.workflowCapabilityService = workflowCapabilityService;
    }

    /** Compatibility constructor used by direct unit tests and legacy embedders. */
    public AgentToolCatalog(AgentToolService agentToolService, AgentToolBindingService bindingService,
                            ToolRegistry toolRegistry,
                            @Qualifier("objectRedisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this(agentToolService, bindingService, toolRegistry, redisTemplate, null, null);
    }

    /**
     * 获取RequestTools。
     */
    public List<AgentTool> getRequestTools(String agentId) {
        return getBoundTools(agentId);
    }

    /**
     * 获取BoundTools。
     */
    public List<AgentTool> getBoundTools(String agentId) {
        String cacheKey = cacheKey(agentId);
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof List) {
                List<AgentTool> cachedTools = (List<AgentTool>) cached;
                if (workflowCapabilityService == null || workflowCacheMatches(agentId, cachedTools)) {
                    return cachedTools;
                }
                log.info("工作流能力版本已变化，忽略旧工具缓存: agentId={}", agentId);
            }
        } catch (Exception e) {
            log.warn("读取Agent工具缓存失败: agentId={}", agentId, e);
        }
        List<AgentToolBinding> bindings = bindingService.list(Wrappers.lambdaQuery(AgentToolBinding.class)
                .eq(AgentToolBinding::getAgentDefinitionId, agentId).eq(AgentToolBinding::getStatus, 1)
                .eq(AgentToolBinding::getDeleted, false).orderByAsc(AgentToolBinding::getPriority));
        List<AgentTool> tools = new ArrayList<>();
        for (AgentToolBinding binding : bindings) {
            AgentTool tool = agentToolService.getById(binding.getToolId());
            if (tool == null) tool = toolRegistry.getTool(binding.getToolId());
            if (tool != null && !Boolean.TRUE.equals(tool.getDeleted()) && Integer.valueOf(1).equals(tool.getStatus())) {
                tools.add(tool);
            }
        }
        AgentDefinition agent = agentDefinitionService == null ? null : agentDefinitionService.getById(agentId);
        if (agent != null && workflowCapabilityService != null && StringUtils.isNotBlank(agent.getApplicationId())) {
            List<AgentWorkflowCapability> capabilities = workflowCapabilityService.listEnabledForAgent(
                    agentId, agent.getApplicationId());
            if (capabilities != null && !capabilities.isEmpty()) {
                addUnifiedWorkflowTools(tools);
            }
        }
        try {
            redisTemplate.opsForValue().set(cacheKey, tools, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("写入Agent工具缓存失败: agentId={}", agentId, e);
        }
        return tools;
    }

    /**
     * 所有工作流能力共用固定工具协议，能力由 workflow_start 的 capabilityCode 选择，
     * 后续动作通过 invocationId 选择实例。这样工作流数量增长不会线性增加工具数量。
     */
    private void addUnifiedWorkflowTools(List<AgentTool> tools) {
        tools.add(unifiedWorkflowTool("START", "workflow_start",
                actionSchema(new String[]{"capabilityCode", "input"},
                        property("capabilityCode", "string", "已绑定且已发布的工作流能力编码"),
                        objectProperty("input", "工作流定义的输入参数"),
                        property("operationKey", "string", "启动幂等键；不传时使用运行上下文生成")),
                "启动指定工作流能力；先根据 capabilityCode 选择能力，再传入工作流定义的 input。"));
        tools.add(unifiedWorkflowTool("OBSERVE", "workflow_observe",
                actionSchema(new String[]{"invocationId"},
                        property("invocationId", "string", "工作流调用 ID")),
                "观察工作流状态、当前节点、状态版本和下一步动作。"));
        tools.add(unifiedWorkflowTool("STOP", "workflow_stop",
                actionSchema(new String[]{"invocationId", "expectedStateVersion"},
                        property("invocationId", "string", "工作流调用 ID"),
                        property("expectedStateVersion", "integer", "最近一次观察返回的状态版本"),
                        property("reason", "string", "停止原因")),
                "停止已启动的工作流。"));
        tools.add(unifiedWorkflowTool("PROVIDE_AGENT_INPUT", "workflow_provide_input",
                actionSchema(new String[]{"invocationId", "expectedStateVersion", "input"},
                        property("invocationId", "string", "工作流调用 ID"),
                        property("expectedStateVersion", "integer", "最近一次观察返回的状态版本"),
                        objectProperty("input", "仅用于允许 Agent 输入的交互节点")),
                "向明确允许 Agent 输入的交互节点提交结构化输入；不能用于 MCP 授权。"));
        JSONObject decision = property("decision", "string", "MCP 授权决定");
        ((JSONObject) decision.get("schema")).put("enum",
                new JSONArray().fluentAdd("once").fluentAdd("allow_10m").fluentAdd("reject"));
        tools.add(unifiedWorkflowTool("RESOLVE_MCP_APPROVAL", "workflow_resolve_mcp_approval",
                actionSchema(new String[]{"invocationId", "expectedStateVersion", "decision"},
                        property("invocationId", "string", "工作流调用 ID"),
                        property("expectedStateVersion", "integer", "最近一次观察返回的状态版本"),
                        decision),
                "处理当前 MCP 授权节点，只能提交 once、allow_10m 或 reject。"));
        tools.add(unifiedWorkflowTool("SIGNAL_EVENT", "workflow_signal_event",
                actionSchema(new String[]{"invocationId", "expectedStateVersion", "eventType", "eventId"},
                        property("invocationId", "string", "工作流调用 ID"),
                        property("expectedStateVersion", "integer", "最近一次观察返回的状态版本"),
                        property("eventType", "string", "事件类型"),
                        property("eventId", "string", "事件幂等 ID"),
                        property("correlationKey", "string", "关联键"),
                        objectProperty("data", "事件数据")),
                "向等待事件的工作流发送业务事件。"));
        tools.add(unifiedWorkflowTool("RETRY_NODE", "workflow_retry",
                actionSchema(new String[]{"invocationId", "expectedStateVersion", "nodeId"},
                        property("invocationId", "string", "工作流调用 ID"),
                        property("expectedStateVersion", "integer", "最近一次观察返回的状态版本"),
                        property("nodeId", "string", "失败节点 ID"),
                        property("reason", "string", "重试原因")),
                "仅在工作流明确允许且外部结果不为 UNKNOWN 时重试失败节点。"));
        JSONObject state = property("state", "string",
                "粗粒度状态筛选，按工作流实例的真实状态判定（不是调用行的状态）：running 只看未结束，"
                        + "finished 只看已结束（含跑完但未被观察过的），all 等价于不传；"
                        + "与 includeCompleted 同时传时以 state 为准");
        ((JSONObject) state.get("schema")).put("enum",
                new JSONArray().fluentAdd("running").fluentAdd("finished").fluentAdd("all"));
        tools.add(unifiedWorkflowTool("LIST", "workflow_list",
                actionSchema(new String[0],
                        property("invocationId", "string", "精确查询某一条工作流调用 ID"),
                        state,
                        property("createdAfter", "string",
                                "创建时间下界（闭区间）。筛的是调用创建时间，不是工作流开始/完成时间；"
                                        + "接受 ISO-8601（须带 Z 或偏移，如 2026-09-15T10:00:00Z）或 13 位毫秒时间戳"),
                        property("createdBefore", "string", "创建时间上界（闭区间），格式同 createdAfter"),
                        property("capabilityCode", "string", "只查该已绑定能力下的调用"),
                        property("includeCompleted", "boolean",
                                "仅在不带查询参数时生效：是否包含最近已结束的调用及其结果，默认 true；"
                                        + "只想看正在处理的传 false"),
                        property("includeOutput", "boolean",
                                "是否内联已结束调用的结果，默认跟随 includeCompleted"),
                        property("current", "integer", "页码，从 1 开始，默认 1"),
                        property("pageSize", "integer", "每页条数，默认 20，上限 50")),
                "列出本 Agent 为当前用户启动的工作流调用：默认「正在处理的在前，最近已结束的在后」并内联结果"
                        + "（超长会截断）。用于重新接管已启动但丢失 invocationId 的工作流，以及取回已结束调用的结果。"
                        + "传入 invocationId、state、createdAfter、createdBefore、capabilityCode 中任一即切换为"
                        + "参数化查询：按创建时间倒序翻页，total 是匹配总数、returned 是本页条数，"
                        + "truncated 表示本页已被填满、后面可能还有更多。"));
    }

    private AgentTool unifiedWorkflowTool(String action, String name, String schema, String description) {
        AgentTool tool = new AgentTool();
        tool.setId("workflow:" + action.toLowerCase());
        tool.setCode(name);
        tool.setName(name);
        tool.setDescription(description);
        tool.setToolType("workflow");
        tool.setType("workflow");
        tool.setWorkflowToolAction(action);
        tool.setParametersSchema(schema);
        tool.setStatus(1);
        return tool;
    }

    private JSONObject objectProperty(String name, String description) {
        return new JSONObject().fluentPut("name", name).fluentPut("schema",
                new JSONObject().fluentPut("type", "object")
                        .fluentPut("description", description)
                        .fluentPut("additionalProperties", true));
    }

    /** 校验 Redis 中的固定工作流工具集合仍与当前能力绑定状态一致。 */
    private boolean workflowCacheMatches(String agentId, List<AgentTool> cachedTools) {
        if (workflowCapabilityService == null || agentDefinitionService == null) return true;
        AgentDefinition agent = agentDefinitionService.getById(agentId);
        if (agent == null || StringUtils.isBlank(agent.getApplicationId())) return false;
        List<AgentWorkflowCapability> enabled = workflowCapabilityService.listEnabledForAgent(agentId, agent.getApplicationId());
        boolean hasUnifiedTools = false;
        for (AgentTool tool : cachedTools) {
            if (isWorkflowTool(tool) && StringUtils.isBlank(tool.getWorkflowCapabilityId())) {
                hasUnifiedTools = true;
                break;
            }
        }
        boolean hasEnabledCapabilities = enabled != null && !enabled.isEmpty();
        if (hasUnifiedTools) {
            return hasEnabledCapabilities;
        }
        return !hasEnabledCapabilities;
    }

    private JSONObject property(String name, String type, String description) {
        return new JSONObject().fluentPut("name", name).fluentPut("schema",
                new JSONObject().fluentPut("type", type).fluentPut("description", description));
    }

    /** 将结构化属性包装为严格 JSON Schema。 */
    private String actionSchema(String[] required, JSONObject... properties) {
        JSONObject schema = new JSONObject();
        JSONObject fields = new JSONObject();
        schema.put("type", "object");
        schema.put("properties", fields);
        schema.put("required", new JSONArray().fluentAddAll(java.util.Arrays.asList(required)));
        schema.put("additionalProperties", false);
        for (JSONObject property : properties) {
            if (property == null) continue;
            String name = property.getString("name");
            Object value = property.get("schema");
            if (StringUtils.isNotBlank(name) && value != null) fields.put(name, value);
        }
        return schema.toJSONString();
    }

    private boolean isWorkflowTool(AgentTool tool) {
        return tool != null && ("workflow".equalsIgnoreCase(tool.getToolType())
                || "workflow".equalsIgnoreCase(tool.getType()));
    }

    /**
     * 处理evict。
     */
    public void evict(String agentId) {
        try {
            redisTemplate.delete(cacheKey(agentId));
        } catch (Exception e) {
            log.warn("清理Agent工具缓存失败: agentId={}", agentId, e);
        }
    }

    private String cacheKey(String agentId) {
        String tenantId = CurrentUser.getUser() == null ? "" : CurrentUser.getUser().get("tenantId");
        return CACHE_KEY_PREFIX + tenantId + ":" + agentId;
    }

    /**
     * 处理evict按ToolId。
     */
    public void evictByToolId(String toolId) {
        try {
            List<AgentToolBinding> bindings = bindingService.list(Wrappers.lambdaQuery(AgentToolBinding.class)
                    .eq(AgentToolBinding::getToolId, toolId).eq(AgentToolBinding::getDeleted, false));
            for (AgentToolBinding binding : bindings) {
                evict(binding.getAgentDefinitionId());
            }
        } catch (Exception e) {
            log.warn("按工具ID清理Agent工具缓存失败: toolId={}", toolId, e);
        }
    }
}
