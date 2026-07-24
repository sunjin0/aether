package com.aether.knowledge.service;

import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.entity.KnowledgeReviewTask;

public interface KnowledgeDocumentWorkflowService {
    KnowledgeDocumentVersion createDraft(KnowledgeDocument document, String sourceVersionId);
    KnowledgeDocumentVersion updateDraft(String versionId, String content, String expectedChecksum);
    KnowledgeDocumentVersion applyAiReviewedChanges(String versionId, String content, String expectedChecksum);
    String startAiReview(String versionId);
    KnowledgeReviewTask submit(String versionId, String comment);
    void claim(String taskId);
    String approve(String taskId, String comment);
    void reject(String taskId, String reason);
}
