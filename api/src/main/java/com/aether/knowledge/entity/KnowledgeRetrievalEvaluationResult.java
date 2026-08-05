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
    /** 结果状态：EVALUATED、RETRIEVAL_ERROR 或 INVALID_LABEL。 */
    private String status;
    /** 评测时冻结的问题文本。 */
    private String questionSnapshot;
    /** 评测时冻结的目标文档 ID。 */
    private String expectedDocumentIdSnapshot;
    /** 评测时冻结的目标文档标题。 */
    private String expectedDocumentTitleSnapshot;
    /** 评测时冻结的目标章节路径。 */
    private String expectedSectionPathSnapshot;
    /** 评测时冻结的目标粒度。 */
    private String targetTypeSnapshot;
    /** 评测时解析出的目标 Chunk ID 列表 JSON。 */
    private String expectedChunkIdsSnapshot;
    /** 实际召回 chunk ID 列表 JSON。 */
    private String retrievedChunkIds;
    /** 实际召回项的显示字段 JSON 快照。 */
    private String retrievedItemsSnapshot;
    /** 当前问题 Recall@K。 */
    private Double recallAtK;
    /** 当前问题 MRR。 */
    private Double mrr;
    /** 当前问题 nDCG。 */
    private Double ndcg;
    /** 稳定的执行错误分类。 */
    private String errorCode;
    /** 用于诊断的错误详情。 */
    private String errorMessage;
}
