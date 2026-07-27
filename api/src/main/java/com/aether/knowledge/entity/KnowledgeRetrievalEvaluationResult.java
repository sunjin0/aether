package com.aether.knowledge.entity;
import com.aether.entity.BaseEntity; import com.baomidou.mybatisplus.annotation.TableName; import lombok.Data; import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName("knowledge_retrieval_evaluation_result")
public class KnowledgeRetrievalEvaluationResult extends BaseEntity { private String runId; private String evaluationCaseId; private String status; private String retrievedChunkIds; private Double recallAtK; private Double mrr; private Double ndcg; }
