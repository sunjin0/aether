package com.aether.workflow.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class AgentWorkflowImportRequest {
    @ApiModelProperty(value = "所属业务应用空间", required = false, example = "app-001") private String applicationId;
    @ApiModelProperty(value = "工作流编码", required = false, example = "order-review") private String code;
    @ApiModelProperty(value = "工作流名称", required = true, example = "订单审核") private String name;
    @ApiModelProperty(value = "描述", required = false, example = "审核新订单") private String description;
    @ApiModelProperty(value = "节点定义 JSON", required = true, example = "[]") private String nodes;
    @ApiModelProperty(value = "边定义 JSON", required = true, example = "[]") private String edges;
    @ApiModelProperty(value = "输入字段定义 JSON", required = false, example = "[]") private String inputSchema;
    @ApiModelProperty(value = "输出字段定义 JSON", required = false, example = "[]") private String outputSchema;
    @ApiModelProperty(value = "最大并发实例数，0 表示不限制", required = false, example = "10") private Integer maxConcurrentInstances;
}
