package com.aether.agent.service;

import com.aether.agent.entity.AgentTool;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.skill.entity.AgentDefinitionSkillBinding;
import com.aether.agent.skill.entity.AgentSkill;
import com.aether.agent.skill.entity.AgentSkillVersion;
import com.aether.agent.skill.service.AgentSkillService;
import com.aether.agent.skill.service.impl.AgentSkillVersionServiceImpl;
import com.aether.agent.tools.AgentToolCatalog;
import com.aether.agent.tools.AgentToolLiveness;
import com.aether.workflow.entity.AgentWorkflowCapability;
import com.aether.workflow.service.AgentWorkflowCapabilityService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/**
 * 构建常驻在系统提示中的轻量「能力目录」。工具、Skill、工作流能力统一放在
 * 一个 system message 中，以分类 JSON 描述，避免模型从多段自然语言中猜测能力边界。
 *
 * <p>完整工具 schema 与 Skill 指令仍按路由按需加载；索引只让模型始终知道存在哪些
 * 能力，可回答「你有哪些工具/技能」这类能力询问，不挤占上下文预算。</p>
 */
@Service
public class CapabilityIndexService {
    private static final int MAX_INDEX_TOKENS = 1000;
    private static final int TOKEN_ESTIMATE_DIVISOR = 4;
    private static final int MAX_LINE_CHARS = 100;

    private final AgentToolCatalog toolCatalog;
    private final AgentSkillService skillService;
    private final AgentSkillVersionServiceImpl versionService;
    private final AgentToolLiveness toolLiveness;
    private final AgentDefinitionService agentDefinitionService;
    private final AgentWorkflowCapabilityService workflowCapabilityService;

    /**
     * 创建 {@code CapabilityIndexService} 实例。
     */
    @org.springframework.beans.factory.annotation.Autowired
    public CapabilityIndexService(AgentToolCatalog toolCatalog, AgentSkillService skillService,
                                  AgentSkillVersionServiceImpl versionService, AgentToolLiveness toolLiveness,
                                  AgentDefinitionService agentDefinitionService,
                                  AgentWorkflowCapabilityService workflowCapabilityService) {
        this.toolCatalog = toolCatalog;
        this.skillService = skillService;
        this.versionService = versionService;
        this.toolLiveness = toolLiveness;
        this.agentDefinitionService = agentDefinitionService;
        this.workflowCapabilityService = workflowCapabilityService;
    }

    /** Compatibility constructor for direct unit tests and embedders without workflow bindings. */
    public CapabilityIndexService(AgentToolCatalog toolCatalog, AgentSkillService skillService,
                                  AgentSkillVersionServiceImpl versionService, AgentToolLiveness toolLiveness) {
        this(toolCatalog, skillService, versionService, toolLiveness, null, null);
    }

    /**
     * 生成单条能力目录消息；无工具、Skill、工作流时返回空串。
     */
    public String buildIndex(String agentId, List<AgentDefinitionSkillBinding> installations) {
        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("type", "agent_capability_catalog");
        catalog.put("version", 1);
        List<Map<String, Object>> tools = new ArrayList<>();
        appendTools(tools, toolLiveness.filterLive(toolCatalog.getBoundTools(agentId)));
        List<Map<String, Object>> skills = new ArrayList<>();
        appendSkills(skills, installations);
        List<Map<String, Object>> workflows = new ArrayList<>();
        appendWorkflows(workflows, agentId);
        if (tools.isEmpty() && skills.isEmpty() && workflows.isEmpty()) return "";
        Map<String, Object> categories = new LinkedHashMap<>();
        categories.put("tools", tools);
        categories.put("skills", skills);
        categories.put("workflows", workflows);
        catalog.put("categories", categories);
        return "\n\n[可用能力 / Available capabilities]\n" + JSON.toJSONString(catalog);
    }

    private void appendTools(List<Map<String, Object>> output, List<AgentTool> tools) {
        if (tools == null) return;
        for (AgentTool tool : tools) {
            if (tool == null || StringUtils.isBlank(tool.getName())) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", tool.getName());
            item.put("description", StringUtils.defaultString(oneLine(tool.getDescription()), ""));
            item.put("input", inputSummary(tool.getParametersSchema(), tool.getMcpInputSchema()));
            item.put("availability", "当前 Agent 已绑定且 MCP 服务启用");
            output.add(item);
            if (JSON.toJSONString(output).length() / TOKEN_ESTIMATE_DIVISOR > MAX_INDEX_TOKENS - 100) {
                output.remove(output.size() - 1);
                return;
            }
        }
    }

    private void appendSkills(List<Map<String, Object>> output, List<AgentDefinitionSkillBinding> installations) {
        if (installations == null) return;
        for (AgentDefinitionSkillBinding binding : installations) {
            if (binding == null) continue;
            AgentSkill skill = skillService.getById(binding.getSkillId());
            AgentSkillVersion version = versionService.getById(binding.getSkillVersionId());
            if (skill == null || version == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", skill.getName());
            item.put("description", StringUtils.defaultString(oneLine(skill.getDescription()), ""));
            item.put("input", inputSummary(version.getInputSchema(), null));
            item.put("availability", "已安装且请求命中路由");
            output.add(item);
            if (JSON.toJSONString(output).length() / TOKEN_ESTIMATE_DIVISOR > MAX_INDEX_TOKENS - 100) {
                output.remove(output.size() - 1);
                return;
            }
        }
    }

    private void appendWorkflows(List<Map<String, Object>> output, String agentId) {
        if (agentDefinitionService == null || workflowCapabilityService == null) return;
        AgentDefinition agent = agentDefinitionService.getById(agentId);
        if (agent == null || StringUtils.isBlank(agent.getApplicationId())) return;
        List<AgentWorkflowCapability> capabilities = workflowCapabilityService.listEnabledForAgent(agentId, agent.getApplicationId());
        if (capabilities == null) return;
        for (AgentWorkflowCapability capability : capabilities) {
            if (capability == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", StringUtils.defaultIfBlank(capability.getDisplayName(), capability.getCapabilityCode()));
            item.put("description", StringUtils.defaultString(oneLine(capability.getDescription()), ""));
            item.put("code", capability.getCapabilityCode());
            item.put("riskLevel", capability.getRiskLevel());
            item.put("actions", parseActions(capability.getAllowedActions()));
            item.put("actionContracts", actionContracts(capability.getAllowedActions()));
            item.put("inputSchema", parseStructuredJson(capability.getInputSchema()));
            item.put("outputSchema", parseStructuredJson(capability.getOutputSchema()));
            item.put("availability", "已绑定且工作流能力已启用");
            output.add(item);
        }
    }

    private List<String> parseActions(String serialized) {
        if (StringUtils.isBlank(serialized)) return Collections.emptyList();
        try { return JSON.parseArray(serialized).toJavaList(String.class); }
        catch (Exception ignored) { return Collections.emptyList(); }
    }

    private List<Map<String, Object>> actionContracts(String serialized) {
        List<Map<String, Object>> contracts = new ArrayList<>();
        addActionContract(contracts, serialized, "START", "启动工作流", Collections.emptyList(), "能力输入参数");
        addActionContract(contracts, serialized, "OBSERVE", "读取状态、当前节点、状态版本和下一步动作",
                Collections.singletonList("invocationId"), "任何运行中调用");
        addActionContract(contracts, serialized, "STOP", "停止工作流",
                java.util.Arrays.asList("invocationId", "expectedStateVersion"), "必须先 OBSERVE");
        addActionContract(contracts, serialized, "PROVIDE_AGENT_INPUT", "向 AGENT_INPUT_ALLOWED 交互节点提交结构化输入",
                java.util.Arrays.asList("invocationId", "expectedStateVersion", "input"), "只能用于 Agent 输入交互，不能用于 MCP 授权");
        addActionContract(contracts, serialized, "RESOLVE_MCP_APPROVAL", "处理 MCP 工具授权节点",
                java.util.Arrays.asList("invocationId", "expectedStateVersion", "decision"), "decision=once|allow_10m|reject，必须有用户明确授权");
        addActionContract(contracts, serialized, "SIGNAL_EVENT", "向等待事件的实例发送事件",
                java.util.Arrays.asList("invocationId", "expectedStateVersion", "eventType", "eventId"), "必须先 OBSERVE");
        addActionContract(contracts, serialized, "RETRY_NODE", "重试失败节点",
                java.util.Arrays.asList("invocationId", "expectedStateVersion", "nodeId"), "外部结果 UNKNOWN 时禁止自动重试");
        return contracts;
    }

    private void addActionContract(List<Map<String, Object>> contracts, String serialized, String action,
                                   String description, List<String> required, String precondition) {
        if (!parseActions(serialized).contains(action)) return;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("action", action);
        item.put("description", description);
        item.put("required", required);
        item.put("precondition", precondition);
        contracts.add(item);
    }

    /** 保持输入/输出契约为 JSON 结构，避免将 Schema 作为不可解析的转义字符串交给模型。 */
    private Object parseStructuredJson(String serialized) {
        if (StringUtils.isBlank(serialized)) return Collections.emptyMap();
        try { return JSON.parse(serialized); }
        catch (Exception ignored) { return Collections.emptyMap(); }
    }

    /**
     * 单行描述：去空白并截断。
     */
    private String oneLine(String value) {
        if (StringUtils.isBlank(value)) return null;
        String single = StringUtils.normalizeSpace(value);
        return single.length() <= MAX_LINE_CHARS ? single : single.substring(0, MAX_LINE_CHARS) + "…";
    }

    /** Only a field-name summary belongs in the permanent catalog; full schemas are runtime-loaded. */
    private String inputSummary(String primarySchema, String fallbackSchema) {
        String schemaText = StringUtils.defaultIfBlank(primarySchema, fallbackSchema);
        if (StringUtils.isBlank(schemaText)) return "无参数";
        try {
            JSONObject schema = JSON.parseObject(schemaText);
            JSONObject properties = schema == null ? null : schema.getJSONObject("properties");
            if (properties == null || properties.isEmpty()) return "结构化参数";
            StringBuilder names = new StringBuilder();
            for (String name : properties.keySet()) {
                if (names.length() > 0) names.append(", ");
                names.append(name);
                if (names.length() >= 80) {
                    names.append("…");
                    break;
                }
            }
            return names.toString();
        } catch (Exception ignored) {
            return "结构化参数";
        }
    }

}
