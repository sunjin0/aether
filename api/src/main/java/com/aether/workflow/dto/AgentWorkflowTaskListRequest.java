package com.aether.workflow.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 聊天页查询「本次会话的工作流任务」的入参。
 *
 * <p>刻意不含 {@code agentDefinitionId}：作用域由会话归属推导，客户端不能借参数扩大可见范围。
 * 也不含 {@code status} 过滤 —— 展示状态是从工作流实例状态与能力动作派生出来的，没有 SQL 表达，
 * 硬做过滤要么过滤不准、要么得伪造 {@code total}。
 *
 * <p>注意 {@code AgentWorkflowTaskListOptions} 的 {@code state} 是另一回事：它只区分
 * 「实例是否已终态」这一档粗粒度，且判定落在 {@code agent_workflow_instance} 表上（不是
 * invocation 行的 status），所以有 SQL 表达。聊天页仍不提供状态筛选，两者不矛盾。
 */
@Data
public class AgentWorkflowTaskListRequest {
    @ApiModelProperty(value = "页码，从 1 开始", required = false, example = "1") private Long current;
    @ApiModelProperty(value = "每页数量，上限 50", required = false, example = "20") private Long pageSize;
    /** 只查某一次运行；服务端会校验该运行确实属于本会话，否则按 404 处理。 */
    @ApiModelProperty(value = "Agent 运行 ID", required = false, example = "2099851728067317762") private String runId;
    /** 默认只看未结束的任务；打开历史会话时传 true 才能看到已完成的行。 */
    @ApiModelProperty(value = "是否包含已结束的任务", required = false, example = "false") private Boolean includeTerminal;
}
