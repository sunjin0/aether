package com.aether.agent.skill.service;

import com.aether.agent.entity.AgentTool;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 一次聊天请求冻结的 Skill 装配结果，禁止在后续执行链路重新放大授权范围。
 */
@Data
public class SkillRuntimeContext {
    private String systemPrompt;
    private List<AgentTool> tools = Collections.emptyList();
    private Set<String> knowledgeBaseIds = Collections.emptySet();
    /**
     * Skill 声明为 required 的工具 id，路由时必须常驻保留。
     */
    private Set<String> requiredToolIds = Collections.emptySet();
    private String snapshot;
    private boolean installed;

    /**
     * 将本次模型调用的预算数据纳入同一份冻结快照。
     */
    public void recordBudget(int inputBudget, int promptTokens, int contextTokens) {
        JSONObject details = snapshot == null ? new JSONObject() : JSON.parseObject(snapshot);
        JSONObject budget = new JSONObject();
        budget.put("inputBudgetTokens", inputBudget);
        budget.put("skillPromptTokens", promptTokens);
        budget.put("contextTokens", contextTokens);
        details.put("budget", budget);
        snapshot = details.toJSONString();
    }
}
