package com.aether.workflow.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class AgentWorkflowListInstancesRequest {
    @ApiModelProperty(value = "页码", required = true, example = "1") private Long current;
    @ApiModelProperty(value = "每页数量", required = true, example = "10") private Long pageSize;
    @ApiModelProperty(value = "工作流 ID", required = false, example = "workflow-001") private String workflowId;
    @ApiModelProperty(value = "业务类型", required = false, example = "ORDER") private String businessType;
    @ApiModelProperty(value = "业务标识", required = false, example = "order-001") private String businessId;
    @ApiModelProperty(value = "实例状态", required = false, example = "RUNNING") private String status;
}
