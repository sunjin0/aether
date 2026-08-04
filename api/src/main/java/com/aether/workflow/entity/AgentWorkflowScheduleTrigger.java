package com.aether.workflow.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 以 Cron 驱动业务工作流的持久化调度定义。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_workflow_schedule_trigger")
public class AgentWorkflowScheduleTrigger extends BaseEntity {
    private String workflowId;
    private String serviceAccountId;
    private String name;
    private String cronExpression;
    private String businessType;
    /** 支持 ${scheduledAt}、${triggerId} 和静态文本。 */
    private String businessIdTemplate;
    private String variables;
    private Boolean enabled;
    private Long nextFireAt;
    private Long lockedUntil;
    private Long lastTriggeredAt;
    private String lastErrorMessage;
    @TableField(exist = false)
    private Long current;
    @TableField(exist = false)
    private Long pageSize;
}
