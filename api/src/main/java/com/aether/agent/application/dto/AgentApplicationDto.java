package com.aether.agent.application.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/** 业务应用空间写入参数。 */
@Data
@ApiModel("业务应用创建或更新请求")
public class AgentApplicationDto {
    @ApiModelProperty(value = "所属租户 ID")
    private String tenantId;
    @ApiModelProperty(value = "唯一应用编码", required = true, example = "support-platform")
    private String code;
    @ApiModelProperty(value = "应用名称", required = true, example = "Support Platform")
    private String name;
    @ApiModelProperty(value = "应用描述", example = "Customer-support automation workspace")
    private String description;
    @ApiModelProperty(value = "状态：0-禁用，1-启用", example = "1")
    private Integer status;
    @ApiModelProperty(value = "每小时最大智能体调用次数", example = "1000")
    private Integer maxAgentCallsPerHour;
    @ApiModelProperty(value = "每小时最大工作流启动次数", example = "100")
    private Integer maxWorkflowStartsPerHour;
}
