package com.aether.workflow.vo;

import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.entity.AgentWorkflowNodeInstance;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 表示智能体工作流InstanceVO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentWorkflowInstanceVo extends AgentWorkflowInstance {
    private String workflowName;
    private List<AgentWorkflowNodeInstance> nodes;
    private String versionNodes;
    private String versionEdges;
    private Long current;
    private Long pageSize;
    /** 父流程处于 WAITING_SUBFLOW 时，穿透展示子流程当前的人工交互/审批等待；否则为 null。 */
    private AgentWorkflowPendingSubflowInteraction pendingSubflowInteraction;
}
