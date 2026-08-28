package com.aether.workflow.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class AgentWorkflowListWebhooksRequest {
    @ApiModelProperty(value = "页码", required = true, example = "1") private Long current;
    @ApiModelProperty(value = "每页数量", required = true, example = "10") private Long pageSize;
    @ApiModelProperty(value = "工作流 ID", required = false, example = "workflow-001") private String workflowId;
}
