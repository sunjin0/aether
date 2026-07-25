package com.aether.knowledge.service;

import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.entity.KnowledgeReviewTask;
import org.springframework.transaction.annotation.Transactional;

public interface KnowledgeDocumentWorkflowService {
    /**
     * 创建草稿
     * @param document 文档
     * @param sourceVersionId 源版本ID
     * @return 创建的草稿版本
     */
    KnowledgeDocumentVersion createDraft(KnowledgeDocument document, String sourceVersionId);
    /**
     * 更新草稿
     * @param versionId 版本ID
     * @param content 内容
     * @param expectedChecksum 期望的校验和
     * @return 更新的草稿版本
     */
    KnowledgeDocumentVersion updateDraft(String versionId, String content, String expectedChecksum);
    /**
     * 应用AI审核更改
     * @param versionId 版本ID
     * @param content 内容
     * @param expectedChecksum 期望的校验和
     * @return 应用AI审核更改后的版本
     */
    KnowledgeDocumentVersion applyAiReviewedChanges(String versionId, String content, String expectedChecksum);
    /**
     * 启动AI审核
     * @param versionId 版本ID
     * @return AI审核任务ID
     */
    String startAiReview(String versionId);
    /**
     * 提交审核
     * @param versionId 版本ID
     * @param comment 审核意见
     * @return 审核任务
     */
    KnowledgeReviewTask submit(String versionId, String comment);

    @Transactional(rollbackFor = Exception.class)
    void claim(String taskId);

    /**
     * 批准审核
     * @param taskId 任务ID
     * @param comment 批准意见
     */
    String approve(String taskId, String comment);
    /**
     * 拒绝审核
     * @param taskId 任务ID
     * @param reason 拒绝原因
     */
    void reject(String taskId, String reason);

    /**
     * 审核人员手动修改已提交的文档内容
     * @param taskId 审核任务ID
     * @param content 新内容
     * @param expectedChecksum 当前内容的 SHA-256，用于并发校验
     * @return 更新后的版本
     */
    KnowledgeDocumentVersion editReviewContent(String taskId, String content, String expectedChecksum);
}
