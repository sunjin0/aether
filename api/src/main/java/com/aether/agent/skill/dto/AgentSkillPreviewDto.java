package com.aether.agent.skill.dto;

import lombok.Data;

import java.util.Map;

/**
 * 预览请求：携带按已安装 Skill code 组织的样例输入，不调用模型。
 */
@Data
public class AgentSkillPreviewDto {
    /** key = Skill code，value = 对应版本的样例输入（按 input_schema 组织） */
    private Map<String, Map<String, Object>> skillInputs;
}
