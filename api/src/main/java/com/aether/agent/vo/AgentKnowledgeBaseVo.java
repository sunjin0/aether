package com.aether.agent.vo;

import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库 VO（V0.7预留）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentKnowledgeBaseVo extends BaseEntity {

    @ApiModelProperty(value = "关联Agent定义ID")
    private String agentDefinitionId;

    @ApiModelProperty(value = "知识库名称")
    private String name;

    @ApiModelProperty(value = "描述")
    private String description;

    @ApiModelProperty(value = "索引状态：0-未索引，1-索引中，2-已索引")
    private Integer indexStatus;

    @ApiModelProperty(value = "状态：0-禁用，1-启用")
    private Integer status;

    private Long current;
    private Long pageSize;
}
