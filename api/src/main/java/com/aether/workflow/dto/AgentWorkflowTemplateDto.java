package com.aether.workflow.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
/**
 * 表示智能体工作流TemplateDTO。
 */
@Data
@ApiModel("工作流模板创建或查询请求")
public class AgentWorkflowTemplateDto {
    @ApiModelProperty(value = "模板名称；创建或实例化模板时必填", example = "Customer onboarding")
    private String name;
    @ApiModelProperty(value = "模板描述", example = "Reusable workflow for onboarding new customers")
    private String description;
}
