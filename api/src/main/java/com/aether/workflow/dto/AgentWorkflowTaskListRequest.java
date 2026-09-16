package com.aether.workflow.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 聊天页查询「本次会话的工作流任务」的入参。
 *
 * <p>刻意不含 {@code agentDefinitionId}：作用域由会话归属推导，客户端不能借参数扩大可见范围。
 * 也不含展示状态之外的细分状态过滤 —— 展示状态是从工作流实例状态与能力动作派生出来的，
 * 没有 SQL 表达，硬做过滤要么过滤不准、要么得伪造 {@code total}。
 *
 * <p><b>{@code state} 与 {@code includeTerminal} 的优先级</b>：一旦传了 {@code state}，就按
 * {@code agent_workflow_instance} 的终态真值判定，{@code includeTerminal} 被完全忽略，
 * {@code state = all} 表示两种终态都不过滤；只有不带 {@code state} 时才回落到
 * {@code includeTerminal} 的旧语义（不传即 false，只看未结束的行）。
 *
 * <p>为什么 {@code all} 也要「接管」而不是当成没传：{@code AgentWorkflowTaskListOptions} 里
 * {@code all} 的语义是「等价于不传」，但聊天页的三档控件把它当成一个明确的选择；若它退化成
 * {@code includeTerminal} 的默认 false，用户在界面上选「全部」反而只看得到未结束的行。
 */
@Data
public class AgentWorkflowTaskListRequest {
    @ApiModelProperty(value = "页码，从 1 开始", required = false, example = "1") private Long current;
    @ApiModelProperty(value = "每页数量，上限 50", required = false, example = "20") private Long pageSize;
    /** 只查某一次运行；服务端会校验该运行确实属于本会话，否则按 404 处理。 */
    @ApiModelProperty(value = "Agent 运行 ID", required = false, example = "2099851728067317762") private String runId;
    /**
     * 粗粒度状态筛选：{@code running} / {@code finished} / {@code all}。
     *
     * <p>按工作流实例的终态判定，而不是 invocation 行的 status —— 后者只由 observe 和一个
     * 「永远只扫全表最旧 200 条」的后台处理器回写，跑完了却没人观察过的调用会停在 RUNNING，
     * 照它过滤会让 {@code finished} 恰好漏掉用户最想看的那些行。
     */
    @ApiModelProperty(value = "状态筛选：running / finished / all", required = false, example = "all")
    private String state;
    /** 不带 {@code state} 时才生效的旧开关：默认只看未结束的任务。 */
    @ApiModelProperty(value = "是否包含已结束的任务", required = false, example = "false") private Boolean includeTerminal;
}
