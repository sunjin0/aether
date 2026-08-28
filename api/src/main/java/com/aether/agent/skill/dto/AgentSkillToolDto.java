package com.aether.agent.skill.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 草稿中声明的工具依赖及其必需性。
 */
@Data
@ApiModel("技能草稿工具依赖")
public class AgentSkillToolDto {
    @ApiModelProperty(value = "工具 ID", required = true, example = "tool-123")
    private String toolId;
    @ApiModelProperty(value = "此工具是否必须可供技能使用", example = "true")
    private Boolean required;
    @ApiModelProperty(value = "数值越小越先执行", example = "10")
    private Integer priority;
}
