package com.aether.workflow.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class AgentWorkflowInstantiateTemplateRequest {
    @ApiModelProperty(value = "新工作流编码；不传时系统自动生成", required = false, example = "order-review-copy") private String code;
    @ApiModelProperty(value = "新工作流名称", required = false, example = "订单审核") private String name;
    @ApiModelProperty(value = "新工作流描述", required = false, example = "从模板创建") private String description;
}
