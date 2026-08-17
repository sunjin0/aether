package com.aether.knowledge.vo;

import com.aether.knowledge.entity.KnowledgeAiReview;
import com.aether.knowledge.entity.KnowledgeAiReviewIssue;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.entity.KnowledgeReviewActionLog;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 表示知识库审核任务详情VO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KnowledgeReviewTaskDetailVo extends KnowledgeReviewTaskVo {
    private KnowledgeDocumentVo document;
    private KnowledgeDocumentVersion version;
    private KnowledgeAiReview aiReview;
    private List<KnowledgeAiReviewIssue> issues;
    private List<KnowledgeReviewActionLog> actionLogs;
}
