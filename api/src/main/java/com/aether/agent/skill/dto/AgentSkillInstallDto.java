package com.aether.agent.skill.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 将已发布 Skill 版本安装到 Agent 的请求参数。
 */
@Data
@ApiModel("在智能体上安装已发布技能请求")
public class AgentSkillInstallDto {
    @ApiModelProperty(value = "已发布技能版本 ID", required = true, example = "skill-version-123")
    private String skillVersionId;
    @ApiModelProperty(value = "数值越小越先执行", example = "10")
    private Integer priority;
    @ApiModelProperty(value = "状态：0-禁用，1-启用", example = "1")
    private Integer status;
    @ApiModelProperty(value = "技能专属配置覆盖 JSON", example = "{\"tone\":\"concise\"}")
    private String configOverrides;
}
