package com.aether.workflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 父流程等待子流程期间，穿透展示子流程当前的人工交互/审批等待。
 * 仅当父实例处于 WAITING_SUBFLOW 时由 detail() 填充；嵌套子流程沿 link 链解析到最深层等待实例。
 */
@Data
public class AgentWorkflowPendingSubflowInteraction {
    /** 最深层等待交互的子流程实例 ID。 */
    private String childInstanceId;
    private String childWorkflowId;
    private String childWorkflowVersionId;
    private String childWorkflowName;
    /** 子流程实例状态：WAITING_USER / WAITING_SUBFLOW / RUNNING / FAILED / TIMED_OUT 等。 */
    private String status;
    /** 子流程当前等待节点 ID。 */
    private String nodeId;
    private String nodeType;
    /** 交互类型：mcp_tool_approval / group / ask_user / approval，与节点 interactionConfig.type 一致。 */
    private String interactionType;
    private String question;
    private List<Object> questions;
    private String arguments;
    private String approvalMode;
    private String approverServiceAccountId;
    /** 是否允许父流程发起人直接代答（指定审批人的 approval 不允许，需由审批人处理）。 */
    private boolean answerable;
    /** 子流程等待截止时间（Unix 毫秒）。 */
    private Long deadlineAt;
}
