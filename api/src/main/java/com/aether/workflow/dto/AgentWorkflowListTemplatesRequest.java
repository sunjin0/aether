package com.aether.workflow.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class AgentWorkflowListTemplatesRequest {
    @ApiModelProperty(value = "模板名称", required = false, example = "订单审核模板") private String name;
}
