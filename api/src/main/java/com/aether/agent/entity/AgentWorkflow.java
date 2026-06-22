package com.aether.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 工作流（V0.7预留）
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("agent_workflow")
@ApiModel(value = "AgentWorkflow对象", description = "工作流")
public class AgentWorkflow extends BaseEntity {

    @ApiModelProperty(value = "关联Agent定义ID")
    private String agentDefinitionId;

    @ApiModelProperty(value = "工作流名称")
    private String name;

    @ApiModelProperty(value = "描述")
    private String description;

    @ApiModelProperty(value = "节点定义（JSON格式，预留）")
    private String nodes;

    @ApiModelProperty(value = "边定义（JSON格式，预留）")
    private String edges;

    @ApiModelProperty(value = "状态：0-草稿，1-启用，2-禁用")
    private Integer status;
}
