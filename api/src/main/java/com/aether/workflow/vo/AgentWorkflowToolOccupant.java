package com.aether.workflow.vo;

import lombok.Data;

/**
 * 正占用某个 MCP 工具的工作流调用。
 *
 * <p>用途单一：模型在对话里直接调一个工具时，判断这个工具是不是已经被某个正在跑的工作流
 * 在它的工具节点上用了。是的话要拦下来 —— 两条路径共用同一个执行器却各有一套审批门，
 * 放过去就会弹第二张确认框，而且直接调用没有工作流那侧的幂等包裹。
 */
@Data
public class AgentWorkflowToolOccupant {
    private String invocationId;
    private String workflowId;
    private String workflowName;
    /** 正在执行该工具的节点 ID。 */
    private String nodeId;
    private String toolName;
}
