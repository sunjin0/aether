package com.aether.workflow.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

/**
 * 创建或更新定时触发器。
 */
@Data
@ApiModel("定时工作流触发器创建或更新请求")
public class AgentWorkflowScheduleTriggerDto {
    @ApiModelProperty(value = "工作流 ID；创建触发器时必填", required = true, example = "workflow-123")
    private String workflowId;
    @ApiModelProperty(value = "用于启动工作流的服务账号 ID", required = true, example = "service-account-123")
    private String serviceAccountId;
    @ApiModelProperty(value = "触发器名称", required = true, example = "Daily ticket summary")
    private String name;
    @ApiModelProperty(value = "兼容 Quartz 的 Cron 表达式", required = true, example = "0 0 8 * * ?")
    private String cronExpression;
    @ApiModelProperty(value = "每次定时启动的业务事件类型", example = "ticket.daily-summary")
    private String businessType;
    @ApiModelProperty(value = "用于生成业务 ID 的模板", example = "summary-${yyyyMMdd}")
    private String businessIdTemplate;
    @ApiModelProperty(value = "每次定时启动提供的变量", example = "{\"region\":\"us-east\"}")
    private Map<String, Object> variables;
}
