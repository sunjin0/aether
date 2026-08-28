package com.aether.agent.skill.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Request for an AI-generated, not-yet-persisted Skill resource draft.
 */
@Data
@ApiModel("AI 生成技能资源草稿请求")
public class AgentSkillResourceGenerateDto {
    @ApiModelProperty(value = "用于生成草稿的模型目录 ID", required = true, example = "model-123")
    private String modelId;
    @ApiModelProperty(value = "资源类型", required = true, example = "PROMPT_TEMPLATE")
    private String type;
    @ApiModelProperty(value = "资源名称", required = true, example = "Ticket response template")
    private String name;
    @ApiModelProperty(value = "预期用途", required = true, example = "Create concise customer-support replies")
    private String purpose;
    @ApiModelProperty(value = "可选生成说明", example = "Use a friendly, professional tone.")
    private String prompt;
}
