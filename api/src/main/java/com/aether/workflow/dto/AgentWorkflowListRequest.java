package com.aether.workflow.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class AgentWorkflowListRequest {
    @ApiModelProperty(value = "页码", required = true, example = "1") private Long current;
    @ApiModelProperty(value = "每页数量", required = true, example = "10") private Long pageSize;
    @ApiModelProperty(value = "工作流名称", required = false, example = "订单审核") private String name;
    @ApiModelProperty(value = "状态：0-草稿，1-启用，2-禁用", required = false, example = "1") private Integer status;
    @ApiModelProperty(value = "所属业务应用空间", required = false, example = "app-001") private String applicationId;
}
