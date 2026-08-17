package com.aether.knowledge.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 一次评测运行的总体指标快照。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_retrieval_evaluation_run")
public class KnowledgeRetrievalEvaluationRun extends BaseEntity {
    /**
     * 所属评测集 ID。
     */
    private String evaluationSetId;
    /**
     * Immutable dataset version used to create this run when available.
     */
    private String evaluationSetVersionId;
    /**
     * Whether this is the selected regression baseline for its evaluation set.
     */
    private Boolean isBaseline;
    /**
     * 运行时检索配置 JSON 快照。
     */
    private String retrievalConfigSnapshot;
    /**
     * Agent ID frozen when the run was created.
     */
    private String agentDefinitionIdSnapshot;
    /**
     * 运行配置 JSON 快照，例如检索 K 和执行方式。
     */
    private String runConfigSnapshot;
    /**
     * 已冻结的用例和标注 JSON 快照。
     */
    private String datasetSnapshot;
    /**
     * 运行状态：RUNNING、SUCCEEDED、PARTIAL_FAILED 或 FAILED。
     */
    private String status;
    /**
     * 参与指标计算的有效问题数。
     */
    private Integer totalCount;
    /**
     * 无法解析正确文档/章节的标注数。
     */
    private Integer invalidCount;
    /**
     * 检索执行异常的问题数。
     */
    private Integer failedCount;
    /**
     * 错误类别及数量 JSON。
     */
    private String errorSummaryJson;
    /**
     * Recall@K 总体指标。
     */
    private Double recallAtK;
    /**
     * MRR 总体指标。
     */
    private Double mrr;
    /**
     * nDCG 总体指标。
     */
    private Double ndcg;
    /**
     * 运行开始时间，Unix 毫秒时间戳。
     */
    private Long startedAt;
    /**
     * 运行结束时间，Unix 毫秒时间戳。
     */
    private Long finishedAt;
}
