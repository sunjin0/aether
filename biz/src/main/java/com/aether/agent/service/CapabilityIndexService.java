package com.aether.agent.service;

import com.aether.agent.entity.AgentTool;
import com.aether.agent.skill.entity.AgentDefinitionSkillBinding;
import com.aether.agent.skill.entity.AgentSkill;
import com.aether.agent.skill.entity.AgentSkillVersion;
import com.aether.agent.skill.service.AgentSkillService;
import com.aether.agent.skill.service.impl.AgentSkillVersionServiceImpl;
import com.aether.agent.tools.AgentToolCatalog;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 构建常驻在系统提示中的轻量「能力索引」：工具与 Skill 的名称加单行描述。
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

    /**
     * 创建 {@code CapabilityIndexService} 实例。
     */
    public CapabilityIndexService(AgentToolCatalog toolCatalog, AgentSkillService skillService,
                                  AgentSkillVersionServiceImpl versionService) {
        this.toolCatalog = toolCatalog;
        this.skillService = skillService;
        this.versionService = versionService;
    }

    /**
     * 生成能力索引文本；无工具且无 Skill 时返回空串。
     */
    public String buildIndex(String agentId, List<AgentDefinitionSkillBinding> installations) {
        StringBuilder out = new StringBuilder();
        appendToolLines(out, toolCatalog.getBoundTools(agentId));
        appendSkillLines(out, installations);
        if (out.length() == 0) return "";
        String body = out.toString().trim();
        return "\n\n[可用能力 / Available capabilities]\n" + body;
    }

    /**
     * 处理appendToolLines。
     */
    private void appendToolLines(StringBuilder out, List<AgentTool> tools) {
        if (tools == null) return;
        for (AgentTool tool : tools) {
            if (tool == null || StringUtils.isBlank(tool.getName())) continue;
            String description = oneLine(tool.getDescription());
            if (out.length() > 0) out.append('\n');
            out.append("- tool ").append(tool.getName());
            if (description != null) out.append(": ").append(description);
            out.append("；输入：").append(inputSummary(tool.getParametersSchema(), tool.getMcpInputSchema()))
                    .append("；可用条件：当前 Agent 已绑定且 MCP 服务启用");
            if (!withinBudget(out)) return;
        }
    }

    /**
     * 处理appendSkillLines。
     */
    private void appendSkillLines(StringBuilder out, List<AgentDefinitionSkillBinding> installations) {
        if (installations == null) return;
        for (AgentDefinitionSkillBinding binding : installations) {
            if (binding == null) continue;
            AgentSkill skill = skillService.getById(binding.getSkillId());
            AgentSkillVersion version = versionService.getById(binding.getSkillVersionId());
            if (skill == null || version == null) continue;
            String description = oneLine(skill.getDescription());
            if (out.length() > 0) out.append('\n');
            out.append("- skill ").append(skill.getName());
            if (description != null) out.append(": ").append(description);
            out.append("；输入：").append(inputSummary(version.getInputSchema(), null))
                    .append("；可用条件：已安装且请求命中路由");
            if (!withinBudget(out)) return;
        }
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

    /**
     * 判断是否还在预算内。
     */
    private boolean withinBudget(StringBuilder out) {
        return out.length() / TOKEN_ESTIMATE_DIVISOR <= MAX_INDEX_TOKENS;
    }
}
