package com.aether.workflow.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流任务清单的一页。
 *
 * <p>{@code total} 的含义随取数结构而变，两者都不等于 {@code tasks.size()}：
 * <ul>
 *   <li>精选视图（没有筛选参数）两段式拼装，{@code total} 就是拼出来的条数。</li>
 *   <li>参数化查询走 SQL，{@code total} 是 SQL 层的匹配数 —— 可能大于本页返回的条数，
 *       因为展示状态取自工作流实例真值，而实例可能早已结束却没有回写 invocation 行
 *       （见 AgentWorkflowInvocationOutboxProcessor 只扫最旧 200 条的历史缺陷）。</li>
 * </ul>
 *
 * <p><b>指引翻页的是 {@code truncated}，不是 {@code total}。</b>它的含义是「本页被填满了，
 * 后面大概率还有」——由本页条数推出，而不是由 {@code total} 减出来。用 {@code total} 推的话，
 * 「匹配 7 条但只回 1 条」会置 true，调用方翻到第二页拿到空集却仍看到 true，就此无限翻页。
 */
@Data
public class AgentWorkflowTaskPage {
    private List<AgentWorkflowTaskVo> tasks = new ArrayList<>();
    private long total;
    private boolean truncated;
}
