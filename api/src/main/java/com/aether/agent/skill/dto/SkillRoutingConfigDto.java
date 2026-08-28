package com.aether.agent.skill.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Global discovery configuration. It does not change frozen Skill versions.
 */
@Data
@ApiModel("全局技能发现配置请求")
public class SkillRoutingConfigDto {
    @ApiModelProperty(value = "用于嵌入技能发现内容的模型目录 ID", required = true, example = "model-embedding-123")
    private String embeddingModelId;
}
