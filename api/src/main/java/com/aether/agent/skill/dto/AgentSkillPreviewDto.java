package com.aether.agent.skill.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

/**
 * 预览请求：携带按已安装 Skill code 组织的样例输入，不调用模型。
 */
@Data
@ApiModel("已安装技能预览请求")
public class AgentSkillPreviewDto {
    /**
     * key = Skill code，value = 对应版本的样例输入（按 input_schema 组织）
     */
    @ApiModelProperty(value = "以已安装技能编码为键的示例输入", example = "{\"summarize\":{\"text\":\"Long support transcript\"}}")
    private Map<String, Map<String, Object>> skillInputs;
}
