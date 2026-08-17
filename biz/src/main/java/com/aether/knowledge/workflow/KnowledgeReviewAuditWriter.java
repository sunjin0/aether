package com.aether.knowledge.workflow;

import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.knowledge.entity.KnowledgeReviewActionLog;
import com.aether.knowledge.service.KnowledgeReviewActionLogService;
import org.springframework.stereotype.Component;

/**
 * 表示知识库审核AuditWriter。
 */
@Component
public class KnowledgeReviewAuditWriter {
    private final KnowledgeReviewActionLogService actionLogService;

    /**
     * 创建 {@code KnowledgeReviewAuditWriter} 实例。
     */
    public KnowledgeReviewAuditWriter(KnowledgeReviewActionLogService actionLogService) {
        this.actionLogService = actionLogService;
    }

    /**
     * 处理write。
     */
    public void write(String operatorId, String taskId, String documentId, String versionId,
                      String action,
                      String before, String after, String comment) {
        KnowledgeReviewActionLog entry = new KnowledgeReviewActionLog();
        entry.setReviewTaskId(taskId);
        entry.setDocumentId(documentId);
        entry.setDocumentVersionId(versionId);
        entry.setOperatorId(operatorId);
        entry.setAction(action);
        entry.setBeforeStatus(before);
        entry.setAfterStatus(after);
        entry.setComment(comment);
        if (!actionLogService.save(entry)) {
            throw new ServerException(500,
                    I18nUtils.getMessage("knowledge.review-action-log.save.failed"));
        }
    }
}
