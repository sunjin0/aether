package com.aether.workflow.dto;

import lombok.Data;

/**
 * 工作流任务清单的取数选项。
 *
 * <p>默认值刻意偏向「看得见」：工作流一完成就从清单里消失，会让模型把「已经做完了」
 * 误判成「不存在了」，进而向用户编造失败结论。所以在跑的之外默认补上最近已结束的。
 *
 * <p><b>查询字段一律用装箱类型，null 表示「调用方没传」—— 这是载荷语义，不是风格选择。</b>
 * 若用基本类型，下面的 {@link #isQueryRequested()} 会恒真，精选视图就再也走不到了。
 *
 * <p>这里有两类字段，作用不同：
 * <ul>
 *   <li><b>筛选参数</b>（{@code invocationId} / {@code state} / {@code createdAfter} /
 *       {@code createdBefore} / {@code capabilityId}）会把取数结构从「两段式精选」切换成
 *       参数化查询，见 {@link #isQueryRequested()}。</li>
 *   <li><b>页码与开关</b>（{@code current} / {@code pageSize} / {@code withResult}）在两种
 *       结构下都生效，不会触发切换 —— 让一个被当成装饰的参数改变取数结构，会让调用方
 *       在毫无察觉的情况下丢掉「在跑优先」的保证。</li>
 * </ul>
 */
@Data
public class AgentWorkflowTaskListOptions {
    /** 页码默认值与上限。与 {@code AgentChatWorkflowController} 同名常量同义，改一处要改两处。 */
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;
    /**
     * 页偏移上限。{@code (current - 1) * pageSize} 用 long 算完再夹一道，
     * 免得病态页码把 OFFSET 推到天文数字上去。
     */
    public static final long MAX_OFFSET = 1_000_000L;

    /** 正在处理中的调用条数上限。 */
    private int inFlightLimit = 20;
    /** 是否附带最近已结束的调用。 */
    private boolean includeTerminal = true;
    /** 已结束调用的条数上限。 */
    private int terminalLimit = 5;
    /**
     * 是否为已结束的调用解析业务结果。
     *
     * <p>解析要读工作流版本与能力（拿输出契约），关掉可省下这些查询；
     * 只在确实要展示结果时才打开。
     */
    private boolean withResult;

    /** 精确查询某一条调用。 */
    private String invocationId;
    /**
     * 粗粒度状态筛选：{@code running} / {@code finished} / {@code all}。
     *
     * <p>按 <b>工作流实例</b>的状态判定，而不是 invocation 行的 status —— 后者只由
     * observe 和一个「只扫最旧 200 条」的后台处理器回写，跑完了却没人观察过的调用会
     * 永远停在 RUNNING。若照它过滤，{@code finished} 会恰好漏掉用户真正想要的那些行。
     */
    private String state;
    /** 创建时间下界（毫秒，闭区间）。筛选的是 createdAt，不是 startedAt/completedAt。 */
    private Long createdAfter;
    /** 创建时间上界（毫秒，闭区间）。 */
    private Long createdBefore;
    /** 能力主键。工具层负责把模型给的 capabilityCode 解析成它。 */
    private String capabilityId;

    /** 页码，从 1 开始。未传时为 null。 */
    private Integer current;
    /** 每页条数，上限 {@link #MAX_PAGE_SIZE}。未传时为 null。 */
    private Integer pageSize;

    /**
     * 是否走参数化查询模式。
     *
     * <p>只认筛选参数。页码与开关刻意不算 —— 模型出于谨慎随手传个 {@code pageSize} 就掉进
     * 「按创建时间取最新 N 条」的话，对一个积压了已结束运行的 agent，拿回来的可能全是已结束
     * 的行，模型就再也看不到自己正在等的那个工作流了。
     *
     * <p>字符串一律按「非空白」判定：模型会发 {@code ""}，空串不该算一次筛选。
     */
    public boolean isQueryRequested() {
        return hasText(invocationId) || isFilteringState() || createdAfter != null
                || createdBefore != null || hasText(capabilityId);
    }

    /** {@code all} 等价于不筛，不该把取数切进查询模式。 */
    public boolean isFilteringState() {
        return hasText(state) && !"all".equalsIgnoreCase(state.trim());
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
