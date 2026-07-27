package com.aether.knowledge.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 评测集中的单条问题及其正确文档/章节标注。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_retrieval_evaluation_case")
public class KnowledgeRetrievalEvaluationCaseEntity extends BaseEntity {
    /** 所属评测集 ID。 */
    private String evaluationSetId;
    /** 用户真实问题。 */
    private String question;
    /** 期望命中的文档 ID。 */
    private String documentId;
    /** 期望命中的章节路径；为空表示整篇文档。 */
    private String sectionPath;
    /** 标注备注。 */
    private String remark;
    /** 启用状态：1 启用，0 停用。 */
    private Integer status;
}
