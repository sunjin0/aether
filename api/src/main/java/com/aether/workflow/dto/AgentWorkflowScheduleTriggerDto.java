package com.aether.workflow.dto;

import lombok.Data;
import java.util.Map;

/** 创建或更新定时触发器。 */
@Data
public class AgentWorkflowScheduleTriggerDto {
    private String workflowId;
    private String serviceAccountId;
    private String name;
    private String cronExpression;
    private String businessType;
    private String businessIdTemplate;
    private Map<String, Object> variables;
}
