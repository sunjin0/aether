package com.aether.workflow.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class AgentWorkflowCreateTemplateRequest {
    @ApiModelProperty(value = "模板名称", required = false, example = "订单审核模板") private String name;
    @ApiModelProperty(value = "模板描述", required = false, example = "通用订单审核流程") private String description;
}
