package com.aether.agent.skill.service;

import com.aether.agent.entity.AgentTool;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/** 一次聊天请求冻结的 Skill 装配结果，禁止在后续执行链路重新放大授权范围。 */
@Data
public class SkillRuntimeContext {
    private String systemPrompt;
    private List<AgentTool> tools = Collections.emptyList();
    private Set<String> knowledgeBaseIds = Collections.emptySet();
    private String snapshot;
    private boolean installed;
}
