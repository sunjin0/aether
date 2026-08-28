package com.aether.workflow.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

@Data
public class AgentWorkflowUpdateScheduleRequest {
    @ApiModelProperty(value = "工作流 ID", required = true, example = "workflow-001") private String workflowId;
    @ApiModelProperty(value = "服务账号 ID", required = true, example = "service-account-001") private String serviceAccountId;
    @ApiModelProperty(value = "定时任务名称", required = true, example = "每日订单审核") private String name;
    @ApiModelProperty(value = "Cron 表达式", required = true, example = "0 0 9 * * ?") private String cronExpression;
    @ApiModelProperty(value = "业务类型", required = true, example = "ORDER") private String businessType;
    @ApiModelProperty(value = "业务标识模板", required = true, example = "daily-${scheduledAt}") private String businessIdTemplate;
    @ApiModelProperty(value = "开始节点变量", required = false, example = "{\"source\":\"schedule\"}") private Map<String, Object> variables;
}
