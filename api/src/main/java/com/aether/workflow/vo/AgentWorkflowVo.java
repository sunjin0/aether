package com.aether.workflow.vo;

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

    @ApiModelProperty(value = "所属业务应用空间")
    private String applicationId;

    @ApiModelProperty(value = "面向业务调用的工作流编码")
    private String code;

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

    @ApiModelProperty(value = "开始节点表单字段（JSON格式）")
    private String inputSchema;

    @ApiModelProperty(value = "业务流程最终输出字段（JSON格式）")
    private String outputSchema;

    @ApiModelProperty(value = "当前发布版本号")
    private Integer publishedVersion;

    @ApiModelProperty(value = "最大并发实例数，0 表示不限制")
    private Integer maxConcurrentInstances;

    @ApiModelProperty(value = "当前发布版本的开始表单字段（JSON格式）")
    private String publishedInputSchema;

    @ApiModelProperty(value = "当前发布版本的最终输出字段（JSON格式）")
    private String publishedOutputSchema;

    private Long current;
    private Long pageSize;
}
