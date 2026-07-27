package com.aether.knowledge.entity;
import com.aether.entity.BaseEntity; import com.baomidou.mybatisplus.annotation.TableName; import lombok.Data; import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName("knowledge_retrieval_evaluation_run")
public class KnowledgeRetrievalEvaluationRun extends BaseEntity { private String evaluationSetId; private String retrievalConfigSnapshot; private Integer totalCount; private Integer invalidCount; private Double recallAtK; private Double mrr; private Double ndcg; private Long startedAt; private Long finishedAt; }
