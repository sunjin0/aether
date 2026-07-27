package com.aether.knowledge.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 检索评测集，按 Agent 维度维护一组真实业务问题。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_retrieval_evaluation_set")
public class KnowledgeRetrievalEvaluationSet extends BaseEntity {
    /** 评测使用的 Agent 定义 ID。 */
    private String agentDefinitionId;
    /** 评测集名称。 */
    private String name;
    /** 评测集说明。 */
    private String description;
    /** 启用状态：1 启用，0 停用。 */
    private Integer status;
}
