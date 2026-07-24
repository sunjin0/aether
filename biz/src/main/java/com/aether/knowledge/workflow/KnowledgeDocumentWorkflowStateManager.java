package com.aether.knowledge.workflow;

import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.entity.KnowledgeReviewTask;
import com.aether.knowledge.model.KnowledgeReviewStatus;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeDocumentVersionService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeDocumentWorkflowStateManager {
    private final KnowledgeDocumentService documentService;
    private final KnowledgeDocumentVersionService versionService;

    public KnowledgeDocumentWorkflowStateManager(KnowledgeDocumentService documentService,
                                                 KnowledgeDocumentVersionService versionService) {
        this.documentService = documentService;
        this.versionService = versionService;
    }

    public ActiveSubmission requireActiveSubmission(KnowledgeReviewTask task) {
        KnowledgeDocumentVersion version = requireVersion(task.getDocumentVersionId());
        KnowledgeDocument document = requireDocument(task.getDocumentId());
        if (!StringUtils.equals(version.getKnowledgeDocumentId(), document.getId())
                || !StringUtils.equals(task.getKnowledgeBaseId(), document.getKnowledgeBaseId())) {
            throw changedSubmission();
        }
        if (!StringUtils.equals(document.getSubmittedVersionId(), version.getId())) {
            throw new ServerException(409,
                    I18nUtils.getMessage("knowledge.document.submission-pointer.changed"));
        }
        if (!KnowledgeReviewStatus.SUBMITTED.equals(document.getReviewStatus())
                || !KnowledgeReviewStatus.SUBMITTED.equals(version.getReviewStatus())
                || !StringUtils.equals(task.getSourceChecksum(), version.getContentChecksum())) {
            throw changedSubmission();
        }
        return new ActiveSubmission(document, version);
    }

    public void updateActiveDraftStatus(String documentId, String versionId, String status) {
        boolean updated = documentService.update(Wrappers.lambdaUpdate(KnowledgeDocument.class)
                .eq(KnowledgeDocument::getId, documentId)
                .eq(KnowledgeDocument::getDraftVersionId, versionId)
                .isNull(KnowledgeDocument::getSubmittedVersionId)
                .set(KnowledgeDocument::getReviewStatus, status)
                .set(KnowledgeDocument::getReviewUpdatedAt, System.currentTimeMillis())
                .set(KnowledgeDocument::getDraftVersionId, versionId));
        if (!updated) {
            throw new ServerException(409,
                    I18nUtils.getMessage("knowledge.document.draft-pointer.changed"));
        }
    }

    public void markSubmitted(String documentId, String versionId, long now) {
        boolean updated = documentService.update(Wrappers.lambdaUpdate(KnowledgeDocument.class)
                .eq(KnowledgeDocument::getId, documentId)
                .eq(KnowledgeDocument::getDraftVersionId, versionId)
                .isNull(KnowledgeDocument::getSubmittedVersionId)
                .set(KnowledgeDocument::getReviewStatus, KnowledgeReviewStatus.SUBMITTED)
                .set(KnowledgeDocument::getReviewUpdatedAt, now)
                .set(KnowledgeDocument::getDraftVersionId, null)
                .set(KnowledgeDocument::getSubmittedVersionId, versionId));
        if (!updated) {
            throw new ServerException(409,
                    I18nUtils.getMessage("knowledge.document.draft-pointer.changed"));
        }
    }

    public void finishSubmission(String documentId, String versionId, String status,
                                 long now, Integer indexStatus) {
        boolean updated = documentService.update(Wrappers.lambdaUpdate(KnowledgeDocument.class)
                .eq(KnowledgeDocument::getId, documentId)
                .eq(KnowledgeDocument::getSubmittedVersionId, versionId)
                .eq(KnowledgeDocument::getReviewStatus, KnowledgeReviewStatus.SUBMITTED)
                .set(KnowledgeDocument::getReviewStatus, status)
                .set(KnowledgeDocument::getReviewUpdatedAt, now)
                .set(KnowledgeDocument::getDraftVersionId, null)
                .set(KnowledgeDocument::getSubmittedVersionId, null)
                .set(indexStatus != null, KnowledgeDocument::getIndexStatus, indexStatus));
        if (!updated) {
            throw new ServerException(409,
                    I18nUtils.getMessage("knowledge.document.submission-pointer.changed"));
        }
    }

    private KnowledgeDocumentVersion requireVersion(String id) {
        KnowledgeDocumentVersion version = versionService.getById(id);
        if (version == null || Boolean.TRUE.equals(version.getDeleted())) {
            throw new ServerException(404,
                    I18nUtils.getMessage("knowledge.document-version.not-found"));
        }
        return version;
    }

    private KnowledgeDocument requireDocument(String id) {
        KnowledgeDocument document = documentService.getById(id);
        if (document == null || Boolean.TRUE.equals(document.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("knowledge.document.not-found"));
        }
        return document;
    }

    private ServerException changedSubmission() {
        return new ServerException(409,
                I18nUtils.getMessage("knowledge.document.submitted-content-or-state.changed"));
    }

    public static class ActiveSubmission {
        private final KnowledgeDocument document;
        private final KnowledgeDocumentVersion version;

        public ActiveSubmission(KnowledgeDocument document, KnowledgeDocumentVersion version) {
            this.document = document;
            this.version = version;
        }

        public KnowledgeDocument getDocument() {
            return document;
        }

        public KnowledgeDocumentVersion getVersion() {
            return version;
        }
    }
}
