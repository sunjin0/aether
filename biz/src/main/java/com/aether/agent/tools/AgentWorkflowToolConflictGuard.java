package com.aether.agent.tools;

import com.aether.agent.entity.AgentTool;
import com.aether.workflow.service.AgentWorkflowTaskQueryService;
import com.aether.workflow.vo.AgentWorkflowToolOccupant;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 拦住「模型在对话里直接调用一个正被工作流使用的 MCP 工具」。
 *
 * <p>工作流工具节点与直接调用走的是<b>同一个</b> {@code McpToolExecutor}，却各有一套独立的
 * 审批门：节点用自己的 {@code toolApprovalPolicy}，对话用会话的 {@code tool_approval_policy}。
 * 于是同一次操作会弹两张确认框；更糟的是直接调用那条路没有工作流侧的
 * {@code externalInvocationService} 幂等包裹，重复执行是真的有副作用。
 *
 * <p>这是<b>尽力而为的护栏</b>而非安全边界：查询失败一律放行。让一个辅助判断把正常的
 * 工具调用打成不可用，代价远大于漏拦一次。
 */
@Component
public class AgentWorkflowToolConflictGuard {

    private final AgentWorkflowTaskQueryService taskQueryService;

    /**
     * 创建 {@code AgentWorkflowToolConflictGuard} 实例。
     */
    public AgentWorkflowToolConflictGuard(@Lazy AgentWorkflowTaskQueryService taskQueryService) {
        this.taskQueryService = taskQueryService;
    }

    /**
     * 工具正被本 Agent 为当前用户启动的某个工作流占用时，给出拒绝理由。
     *
     * @param tool             待调用的工具，可为 null
     * @param agentDefinitionId 当前 Agent
     * @param userId           当前用户；工作流只拦「自己发起、自己授权」的那批
     * @return 拒绝理由；无冲突时返回 null 表示放行
     */
    public String describeConflict(AgentTool tool, String agentDefinitionId, String userId) {
        if (tool == null || StringUtils.isBlank(tool.getId()) || taskQueryService == null) return null;
        AgentWorkflowToolOccupant occupant;
        try {
            occupant = taskQueryService.findRunningToolOwner(agentDefinitionId, userId, tool.getId());
        } catch (Exception ignored) {
            return null;
        }
        if (occupant == null || StringUtils.isBlank(occupant.getInvocationId())) return null;
        String invocationId = occupant.getInvocationId();
        return "工具 " + displayName(tool) + " 正由你的工作流调用 " + invocationId
                + "（" + StringUtils.defaultIfBlank(occupant.getWorkflowName(), "未命名工作流") + "）"
                + (StringUtils.isBlank(occupant.getNodeId()) ? "" : "在节点 " + occupant.getNodeId() + " 上")
                + "执行，请勿在对话中重复直接调用。"
                + "用 workflow_observe（invocationId=" + invocationId + "）查看进度；"
                + "若它停在授权上，用 workflow_resolve_mcp_approval 处理。";
    }

    private String displayName(AgentTool tool) {
        return StringUtils.defaultIfBlank(tool.getName(),
                StringUtils.defaultIfBlank(tool.getCode(), tool.getId()));
    }
}
