package com.aether.workflow.vo;

import lombok.Data;

import java.util.Map;

/**
 * 一条 Agent 发起的工作流调用，供「agent 自查在跑的工作流」与「聊天页任务清单」共用。
 *
 * <p>{@code status} 是规范化后的展示状态，{@code rawStatus} 保留实例原始状态；
 * 之所以不在这里返回本地化文案，是因为两个消费方都要自己做 i18n
 * （agent 侧吃 JSON，聊天页是双语的）。
 */
@Data
public class AgentWorkflowTaskVo {
    private String invocationId;
    private String instanceId;
    private String workflowId;
    private String workflowName;
    private String capabilityId;
    private String capabilityCode;
    private String capabilityName;
    /**
     * RUNNING / WAITING_MCP_APPROVAL / WAITING_USER_INPUT / COMPLETED / FAILED / TERMINATED / TIMED_OUT
     */
    private String status;
    /** 实例原始状态；实例行缺失时退回 invocation 的投影状态。 */
    private String rawStatus;
    private Long stateVersion;
    private String currentNodeId;
    private String currentNodeType;
    private String currentNodeName;
    /** 与 workflow_observe 返回的 nextAction 同形状。 */
    private Map<String, Object> nextAction;
    private Long startedAt;
    private Long completedAt;
    /**
     * 终态调用的结果码，取值与 {@code workflow_observe} 一致（WORKFLOW_COMPLETED 等）；非终态为空。
     */
    private String resultCode;
    /**
     * 按输出契约筛选后的业务结果；仅在调用方显式要求解析时才有值。
     *
     * <p>刻意不在这一层截断：截断是「模型上下文预算」的关切，属于 agent 工具层；
     * 聊天页是同一条数据的人类消费者，不该被模型的预算连累。
     */
    private Map<String, Object> output;
}
