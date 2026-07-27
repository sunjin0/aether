package com.aether.knowledge.entity;
import com.aether.entity.BaseEntity; import com.baomidou.mybatisplus.annotation.TableName; import lombok.Data; import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName("knowledge_retrieval_evaluation_case")
public class KnowledgeRetrievalEvaluationCaseEntity extends BaseEntity { private String evaluationSetId; private String question; private String documentId; private String sectionPath; private String remark; private Integer status; }
