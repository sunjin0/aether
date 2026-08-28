package com.aether.workflow.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 工作流草稿编辑请求。
 */
@Data
@ApiModel("工作流创建、更新、导入请求")
public class AgentWorkflowDto {
    @ApiModelProperty(value = "所属应用 ID", required = true, example = "app-support")
    private String applicationId;
    @ApiModelProperty(value = "唯一工作流编码", required = true, example = "ticket-triage")
    private String code;
    @ApiModelProperty(value = "工作流名称", required = true, example = "Support ticket triage")
    private String name;
    @ApiModelProperty(value = "工作流描述", example = "Classifies and routes incoming support tickets")
    private String description;
    @ApiModelProperty(value = "工作流节点 JSON", required = true, example = "[{\"id\":\"start\",\"type\":\"start\"}]")
    private String nodes;
    @ApiModelProperty(value = "工作流边 JSON", required = true, example = "[]")
    private String edges;
    @ApiModelProperty(value = "输入 JSON 架构", example = "{\"type\":\"object\",\"properties\":{\"ticketId\":{\"type\":\"string\"}}}")
    private String inputSchema;
    @ApiModelProperty(value = "输出 JSON 架构", example = "{\"type\":\"object\",\"properties\":{\"category\":{\"type\":\"string\"}}}")
    private String outputSchema;
    @ApiModelProperty(value = "最大并发实例数；省略时使用服务默认值", example = "10")
    private Integer maxConcurrentInstances;
}
