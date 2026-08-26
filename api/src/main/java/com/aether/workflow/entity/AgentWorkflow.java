package com.aether.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * AI 工作流定义。nodes/edges 始终保存当前草稿，发布内容保存在版本快照中。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("agent_workflow")
@ApiModel(value = "AgentWorkflow对象", description = "工作流")
public class AgentWorkflow extends BaseEntity {

    @ApiModelProperty(value = "所属业务应用空间")
    private String applicationId;

    @ApiModelProperty(value = "关联Agent定义ID")
    private String agentDefinitionId;

    @ApiModelProperty(value = "工作流名称")
    private String name;

    @ApiModelProperty(value = "描述")
    private String description;

    @ApiModelProperty(value = "节点定义（JSON格式）")
    private String nodes;

    @ApiModelProperty(value = "边定义（JSON格式，预留）")
    private String edges;

    @ApiModelProperty(value = "状态：0-草稿，1-已发布，2-下线")
    private Integer status;

    @ApiModelProperty(value = "开始节点表单字段（JSON格式）")
    private String inputSchema;

    @ApiModelProperty(value = "业务流程最终输出字段（JSON格式）")
    private String outputSchema;

    @ApiModelProperty(value = "当前发布版本号")
    private Integer publishedVersion;

    /**
     * 同时处于运行或等待人工状态的最大实例数；0 表示不限制。
     */
    private Integer maxConcurrentInstances;
}
