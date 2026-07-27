package com.aether.knowledge.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 一次评测运行的总体指标快照。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_retrieval_evaluation_run")
public class KnowledgeRetrievalEvaluationRun extends BaseEntity {
    /** 所属评测集 ID。 */
    private String evaluationSetId;
    /** 运行时检索配置 JSON 快照。 */
    private String retrievalConfigSnapshot;
    /** 参与指标计算的有效问题数。 */
    private Integer totalCount;
    /** 无法解析正确文档/章节的标注数。 */
    private Integer invalidCount;
    /** Recall@K 总体指标。 */
    private Double recallAtK;
    /** MRR 总体指标。 */
    private Double mrr;
    /** nDCG 总体指标。 */
    private Double ndcg;
    /** 运行开始时间，Unix 毫秒时间戳。 */
    private Long startedAt;
    /** 运行结束时间，Unix 毫秒时间戳。 */
    private Long finishedAt;
}
