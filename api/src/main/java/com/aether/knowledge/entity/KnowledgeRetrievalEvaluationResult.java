package com.aether.knowledge.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 单次运行中某条问题的评测结果及实际召回分块。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_retrieval_evaluation_result")
public class KnowledgeRetrievalEvaluationResult extends BaseEntity {
    /** 所属运行记录 ID。 */
    private String runId;
    /** 对应评测问题 ID。 */
    private String evaluationCaseId;
    /** 结果状态，例如 EVALUATED。 */
    private String status;
    /** 实际召回 chunk ID 列表 JSON。 */
    private String retrievedChunkIds;
    /** 当前问题 Recall@K。 */
    private Double recallAtK;
    /** 当前问题 MRR。 */
    private Double mrr;
    /** 当前问题 nDCG。 */
    private Double ndcg;
}
