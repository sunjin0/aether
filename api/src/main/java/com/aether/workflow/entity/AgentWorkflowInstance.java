package com.aether.workflow.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流一次运行实例。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_workflow_instance")
public class AgentWorkflowInstance extends BaseEntity {
    /** 继承自工作流定义的业务应用空间。 */
    private String applicationId;
    private String workflowId;
    private String workflowVersionId;
    private String userId;
    /**
     * 业务类型，例如 ticket / knowledge_review；由接入方传入。
     */
    private String businessType;
    /**
     * 业务系统中的单据或对象 ID。
     */
    private String businessId;
    /**
     * 业务方请求幂等键，同一工作流、同一发起人下只能创建一个实例。
     */
    private String idempotencyKey;
    /**
     * 终态通知地址；必须匹配服务端白名单。
     */
    private String callbackUrl;
    /**
     * 人工等待 SLA 截止时间（Unix 毫秒）。
     */
    private Long deadlineAt;
    /**
     * RUNNING / WAITING_USER / FAILED / COMPLETED / TERMINATED
     */
    private String status;
    private String variables;
    private String currentNodeId;
    private String errorMessage;
    private Long startedAt;
    private Long completedAt;
}
