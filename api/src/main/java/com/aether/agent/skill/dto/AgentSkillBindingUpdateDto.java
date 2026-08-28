package com.aether.agent.skill.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 更新 Agent 已安装 Skill 的版本、优先级或状态。
 */
@Data
@ApiModel("已安装智能体技能更新请求")
public class AgentSkillBindingUpdateDto {
    @ApiModelProperty(value = "已发布技能版本 ID", required = true, example = "skill-version-123")
    private String skillVersionId;
    @ApiModelProperty(value = "数值越小越先执行", example = "10")
    private Integer priority;
    @ApiModelProperty(value = "状态：0-禁用，1-启用", example = "1")
    private Integer status;
    @ApiModelProperty(value = "技能专属配置覆盖 JSON", example = "{\"tone\":\"concise\"}")
    private String configOverrides;
}
