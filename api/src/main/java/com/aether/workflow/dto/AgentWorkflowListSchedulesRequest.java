package com.aether.workflow.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class AgentWorkflowListSchedulesRequest {
    @ApiModelProperty(value = "页码", required = false, example = "1") private Long current;
    @ApiModelProperty(value = "每页数量", required = false, example = "10") private Long pageSize;
    @ApiModelProperty(value = "定时任务名称", required = false, example = "每日订单审核") private String name;
    @ApiModelProperty(value = "工作流 ID", required = false, example = "workflow-001") private String workflowId;
    @ApiModelProperty(value = "是否启用", required = false, example = "true") private Boolean enabled;
}
