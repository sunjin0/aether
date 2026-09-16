package com.aether.workflow.service;

import com.aether.workflow.dto.AgentWorkflowTaskListOptions;
import com.aether.workflow.vo.AgentWorkflowTaskPage;
import com.aether.workflow.vo.AgentWorkflowToolOccupant;

/**
 * 工作流任务清单的只读查询。
 *
 * <p>刻意不复用 {@link AgentWorkflowInvocationService#observe}：那条路径要求调用方是
 * 调用发起人、能力仍绑定且仍授予 OBSERVE，而且每次调用都会写库。清单是展示用途，
 * 上述任何一条都会让历史任务凭空消失。
 */
public interface AgentWorkflowTaskQueryService {
    /**
     * 本 Agent 为当前用户发起、且仍在处理中的工作流调用。
     *
     * <p>等价于 {@code terminalLimit = 0} 的 {@link #listInFlight(String, String, AgentWorkflowTaskListOptions)}。
     *
     * @param limit 最多返回多少条；同时决定候选窗口大小。
     */
    AgentWorkflowTaskPage listInFlight(String agentDefinitionId, String principalId, int limit);

    /**
     * 按选项取任务清单：在跑的优先，其后接最近已结束的（可关掉）。
     *
     * <p>之所以默认带上已结束的：工作流一完成就从清单里消失，模型会把「已经做完了」
     * 读成「不存在了」，进而向用户编造失败结论。
     */
    AgentWorkflowTaskPage listInFlight(String agentDefinitionId, String principalId,
                                       AgentWorkflowTaskListOptions options);

    /**
     * 某个会话涉及的工作流调用。
     *
     * @param runId          只取这一次运行的调用；为空表示整个会话
     * @param includeTerminal 是否包含已结束的调用
     */
    AgentWorkflowTaskPage listByConversation(String conversationId, String runId, boolean includeTerminal,
                                             int current, int pageSize);

    /**
     * 带筛选项的会话清单。页码、开关与状态筛选走 {@link AgentWorkflowTaskListOptions}。
     *
     * <p>传了 {@code state} 就按工作流实例的终态真值筛，{@code includeTerminal} 被忽略；
     * 只有不带 {@code state} 时才回落到上面那个签名的旧语义。
     */
    AgentWorkflowTaskPage listByConversation(String conversationId, String runId,
                                             AgentWorkflowTaskListOptions options);

    /**
     * 正有工作流在某个工具节点上执行该工具时返回占用者，否则 null。
     *
     * <p>这是「模型直接调用 MCP 工具」那条热路径上的判断，必须便宜：绝大多数 agent 名下
     * 根本没有在跑的工作流，第一次查询为空就该返回。
     *
     * @param toolId {@code agent_tool} 的主键，也就是工具节点定义里的 {@code resourceId}
     */
    AgentWorkflowToolOccupant findRunningToolOwner(String agentDefinitionId, String principalId, String toolId);
}
