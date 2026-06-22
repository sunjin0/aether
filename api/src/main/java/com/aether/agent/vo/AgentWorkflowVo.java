package com.aether.agent.vo;

import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流 VO（V0.7预留）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentWorkflowVo extends BaseEntity {

    @ApiModelProperty(value = "关联Agent定义ID")
    private String agentDefinitionId;

    @ApiModelProperty(value = "工作流名称")
    private String name;

    @ApiModelProperty(value = "描述")
    private String description;

    @ApiModelProperty(value = "节点定义（JSON格式）")
    private String nodes;

    @ApiModelProperty(value = "边定义（JSON格式）")
    private String edges;

    @ApiModelProperty(value = "状态：0-草稿，1-启用，2-禁用")
    private Integer status;

    private Long current;
    private Long pageSize;
}
