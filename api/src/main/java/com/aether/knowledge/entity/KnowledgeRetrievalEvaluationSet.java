package com.aether.knowledge.entity;
import com.aether.entity.BaseEntity; import com.baomidou.mybatisplus.annotation.TableName; import lombok.Data; import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName("knowledge_retrieval_evaluation_set")
public class KnowledgeRetrievalEvaluationSet extends BaseEntity { private String agentDefinitionId; private String name; private String description; private Integer status; }
