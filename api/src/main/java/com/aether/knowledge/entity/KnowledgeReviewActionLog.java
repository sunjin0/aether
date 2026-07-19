package com.aether.knowledge.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_review_action_log")
public class KnowledgeReviewActionLog extends BaseEntity {
    private String reviewTaskId;
    private String documentId;
    private String documentVersionId;
    private String operatorId;
    private String action;
    private String beforeStatus;
    private String afterStatus;
    private String comment;
    private String metadata;
}
