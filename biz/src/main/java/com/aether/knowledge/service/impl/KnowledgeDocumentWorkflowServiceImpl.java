package com.aether.knowledge.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.aether.exception.ServerException;
import com.aether.knowledge.entity.KnowledgeAiReview;
import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.entity.KnowledgeReviewActionLog;
import com.aether.knowledge.entity.KnowledgeReviewTask;
import com.aether.knowledge.mapper.KnowledgeDocumentMapper;
import com.aether.knowledge.model.KnowledgeReviewStatus;
import com.aether.knowledge.service.KnowledgeAccessService;
import com.aether.knowledge.service.KnowledgeAiReviewRecordService;
import com.aether.knowledge.service.KnowledgeAiReviewIssueService;
import com.aether.knowledge.service.KnowledgeDocumentIndexService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeDocumentVersionService;
import com.aether.knowledge.service.KnowledgeDocumentWorkflowService;
import com.aether.knowledge.service.KnowledgeReviewActionLogService;
import com.aether.knowledge.service.KnowledgeReviewTaskService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class KnowledgeDocumentWorkflowServiceImpl implements KnowledgeDocumentWorkflowService {
    private final KnowledgeDocumentService documentService;
    private final KnowledgeDocumentVersionService versionService;
    private final KnowledgeReviewTaskService taskService;
    private final KnowledgeReviewActionLogService actionLogService;
    private final KnowledgeAiReviewRecordService aiReviewRecordService;
    private final KnowledgeAiReviewIssueService aiReviewIssueService;
    private final KnowledgeAccessService accessService;
    private final KnowledgeDocumentIndexService indexService;
    private final KnowledgeAiReviewWorker aiReviewWorker;
    private final TransactionTemplate transactionTemplate;
    private final KnowledgeDocumentMapper documentMapper;

    public KnowledgeDocumentWorkflowServiceImpl(KnowledgeDocumentService documentService,
                                                KnowledgeDocumentVersionService versionService,
                                                KnowledgeReviewTaskService taskService,
                                                KnowledgeReviewActionLogService actionLogService,
                                                KnowledgeAiReviewRecordService aiReviewRecordService,
                                                KnowledgeAiReviewIssueService aiReviewIssueService,
                                                KnowledgeAccessService accessService,
                                                KnowledgeDocumentIndexService indexService,
                                                KnowledgeAiReviewWorker aiReviewWorker,
                                                TransactionTemplate transactionTemplate,
                                                KnowledgeDocumentMapper documentMapper) {
        this.documentService = documentService;
        this.versionService = versionService;
        this.taskService = taskService;
        this.actionLogService = actionLogService;
        this.aiReviewRecordService = aiReviewRecordService;
        this.aiReviewIssueService = aiReviewIssueService;
        this.accessService = accessService;
        this.indexService = indexService;
        this.aiReviewWorker = aiReviewWorker;
        this.transactionTemplate = transactionTemplate;
        this.documentMapper = documentMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentVersion createDraft(KnowledgeDocument document, String sourceVersionId) {
        if (document == null || StringUtils.isBlank(document.getId())) {
            throw new ServerException(400, "knowledge document is required");
        }
        KnowledgeDocument lockedDocument = documentMapper.selectActiveForUpdate(document.getId());
        if (lockedDocument == null) throw new ServerException(404, "knowledge document not found");
        accessService.requireWritable(lockedDocument.getKnowledgeBaseId());
        if (StringUtils.isNotBlank(lockedDocument.getDraftVersionId())
                || StringUtils.isNotBlank(lockedDocument.getSubmittedVersionId())) {
            throw new ServerException(409, "document already has an active draft or review task");
        }
        KnowledgeDocumentVersion version = versionService.createNextVersion(document);
        version.setOriginalContent(document.getContent());
        version.setContentChecksum(checksum(document.getContent()));
        version.setReviewStatus(KnowledgeReviewStatus.DRAFT);
        version.setSourceVersionId(sourceVersionId);
        version.setParserType(document.getParserType());
        versionService.updateById(version);

        KnowledgeDocument update = new KnowledgeDocument();
        update.setId(document.getId());
        update.setDraftVersionId(version.getId());
        update.setSubmittedVersionId(null);
        update.setReviewStatus(KnowledgeReviewStatus.DRAFT);
        update.setReviewUpdatedAt(System.currentTimeMillis());
        updateDocumentPointers(update, true);
        log(null, document.getId(), version.getId(), "DRAFT_CREATED", null,
                KnowledgeReviewStatus.DRAFT, null);
        return version;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentVersion updateDraft(String versionId, String content, String expectedChecksum) {
        KnowledgeDocumentVersion version = requireVersion(versionId);
        KnowledgeDocument document = requireDocument(version.getKnowledgeDocumentId());
        accessService.requireWritable(document.getKnowledgeBaseId());
        if (!StringUtils.equals(document.getDraftVersionId(), versionId)) {
            throw new ServerException(409, "document draft pointer has changed");
        }
        if (!KnowledgeReviewStatus.DRAFT.equals(version.getReviewStatus())
                && !KnowledgeReviewStatus.AI_REVIEWED.equals(version.getReviewStatus())) {
            throw new ServerException(409, "only draft or AI-reviewed versions can be edited");
        }
        if (StringUtils.isBlank(expectedChecksum)) {
            throw new ServerException(400, "expected draft checksum is required");
        }
        if (!StringUtils.equals(expectedChecksum, version.getContentChecksum())) {
            throw new ServerException(409, "document draft has been modified");
        }
        String newChecksum = checksum(content);
        boolean updated = versionService.update(Wrappers.lambdaUpdate(KnowledgeDocumentVersion.class)
                .eq(KnowledgeDocumentVersion::getId, versionId)
                .in(KnowledgeDocumentVersion::getReviewStatus,
                        KnowledgeReviewStatus.DRAFT, KnowledgeReviewStatus.AI_REVIEWED)
                .set(KnowledgeDocumentVersion::getContent, content)
                .set(KnowledgeDocumentVersion::getStructuredContent, null)
                .set(KnowledgeDocumentVersion::getContentChecksum, newChecksum)
                .set(KnowledgeDocumentVersion::getReviewStatus, KnowledgeReviewStatus.DRAFT));
        if (!updated) throw new ServerException(409, "document draft state changed");
        updateDocumentReviewStatus(document.getId(), KnowledgeReviewStatus.DRAFT, versionId, null);
        log(null, document.getId(), versionId, "DRAFT_UPDATED", version.getReviewStatus(),
                KnowledgeReviewStatus.DRAFT, null);
        return versionService.getById(versionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String startAiReview(String versionId) {
        KnowledgeDocumentVersion version = requireVersion(versionId);
        KnowledgeDocument document = requireDocument(version.getKnowledgeDocumentId());
        accessService.requireWritable(document.getKnowledgeBaseId());
        if (!KnowledgeReviewStatus.DRAFT.equals(version.getReviewStatus())) {
            throw new ServerException(409, "AI review can only start from draft state");
        }
        KnowledgeAiReview review = new KnowledgeAiReview();
        review.setKnowledgeBaseId(document.getKnowledgeBaseId());
        review.setDocumentId(document.getId());
        review.setDocumentVersionId(versionId);
        review.setSourceChecksum(version.getContentChecksum());
        review.setPromptVersion("v1");
        review.setStatus("pending");
        aiReviewRecordService.save(review);
        boolean transitioned = versionService.update(Wrappers.lambdaUpdate(KnowledgeDocumentVersion.class)
                .eq(KnowledgeDocumentVersion::getId, versionId)
                .eq(KnowledgeDocumentVersion::getReviewStatus, KnowledgeReviewStatus.DRAFT)
                .set(KnowledgeDocumentVersion::getReviewStatus, KnowledgeReviewStatus.AI_REVIEWING));
        if (!transitioned) throw new ServerException(409, "document draft state changed");
        updateDocumentReviewStatus(document.getId(), KnowledgeReviewStatus.AI_REVIEWING, versionId, null);
        log(null, document.getId(), versionId, "AI_REVIEW_STARTED", KnowledgeReviewStatus.DRAFT,
                KnowledgeReviewStatus.AI_REVIEWING, null);
        dispatchAiReviewAfterCommit(review.getId());
        return review.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeReviewTask submit(String versionId, String comment) {
        KnowledgeDocumentVersion version = requireVersion(versionId);
        KnowledgeDocument document = requireDocument(version.getKnowledgeDocumentId());
        KnowledgeBase base = accessService.requireSubmittable(document.getKnowledgeBaseId());
        if (!StringUtils.equals(document.getDraftVersionId(), versionId)
                || StringUtils.isNotBlank(document.getSubmittedVersionId())) {
            throw new ServerException(409, "document draft pointer has changed");
        }
        boolean aiRequired = booleanConfig(base.getReviewConfig(), "aiReviewRequired", true);
        if (aiRequired && !KnowledgeReviewStatus.AI_REVIEWED.equals(version.getReviewStatus())) {
            throw new ServerException(409, "AI review is required before submission");
        }
        if (!KnowledgeReviewStatus.DRAFT.equals(version.getReviewStatus())
                && !KnowledgeReviewStatus.AI_REVIEWED.equals(version.getReviewStatus())) {
            throw new ServerException(409, "document version cannot be submitted in current state");
        }
        if (KnowledgeReviewStatus.AI_REVIEWED.equals(version.getReviewStatus())
                && booleanConfig(base.getReviewConfig(), "blockOnCriticalIssues", true)
                && hasPendingCriticalIssues(versionId)) {
            throw new ServerException(409, "critical AI review issues must be handled before submission");
        }
        String submitter = accessService.currentAdminId();
        long now = System.currentTimeMillis();
        boolean transitioned = versionService.update(Wrappers.lambdaUpdate(KnowledgeDocumentVersion.class)
                .eq(KnowledgeDocumentVersion::getId, versionId)
                .in(KnowledgeDocumentVersion::getReviewStatus,
                        KnowledgeReviewStatus.DRAFT, KnowledgeReviewStatus.AI_REVIEWED)
                .set(KnowledgeDocumentVersion::getReviewStatus, KnowledgeReviewStatus.SUBMITTED)
                .set(KnowledgeDocumentVersion::getSubmittedBy, submitter)
                .set(KnowledgeDocumentVersion::getSubmittedAt, now));
        if (!transitioned) throw new ServerException(409, "document version state changed");
        KnowledgeReviewTask task = new KnowledgeReviewTask();
        task.setKnowledgeBaseId(document.getKnowledgeBaseId());
        task.setDocumentId(document.getId());
        task.setDocumentVersionId(versionId);
        task.setSubmitterId(submitter);
        task.setStatus("pending");
        task.setSourceChecksum(version.getContentChecksum());
        task.setSubmitComment(comment);
        task.setSubmittedAt(now);
        taskService.save(task);
        updateDocumentReviewStatus(document.getId(), KnowledgeReviewStatus.SUBMITTED, null, versionId);
        log(task.getId(), document.getId(), versionId, "SUBMITTED", version.getReviewStatus(),
                KnowledgeReviewStatus.SUBMITTED, comment);
        return task;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void claim(String taskId) {
        KnowledgeReviewTask task = requireTask(taskId);
        accessService.requireApprovable(task.getKnowledgeBaseId());
        String reviewer = accessService.currentAdminId();
        if (!taskService.claim(taskId, reviewer, System.currentTimeMillis())) {
            throw new ServerException(409, "review task has already been claimed");
        }
        log(taskId, task.getDocumentId(), task.getDocumentVersionId(), "CLAIMED",
                task.getStatus(), "claimed", null);
    }

    @Override
    public String approve(String taskId, String comment) {
        String jobId = transactionTemplate.execute(status -> {
            ApprovalResult approved = approveInTransaction(taskId, comment);
            return indexService.queueReindex(approved.document, approved.version, "approved");
        });
        if (jobId == null) throw new ServerException(500, "failed to approve document");
        return jobId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(String taskId, String reason) {
        if (StringUtils.isBlank(reason)) throw new ServerException(400, "rejection reason is required");
        KnowledgeReviewTask task = requireTask(taskId);
        accessService.requireApprovable(task.getKnowledgeBaseId());
        String reviewer = accessService.currentAdminId();
        ensureReviewer(task, reviewer);
        KnowledgeDocument document = requireDocument(task.getDocumentId());
        if (!StringUtils.equals(document.getSubmittedVersionId(), task.getDocumentVersionId())) {
            throw new ServerException(409, "document submission pointer has changed");
        }
        long now = System.currentTimeMillis();
        boolean updated = taskService.update(Wrappers.lambdaUpdate(KnowledgeReviewTask.class)
                .eq(KnowledgeReviewTask::getId, taskId)
                .in(KnowledgeReviewTask::getStatus, "pending", "claimed")
                .set(KnowledgeReviewTask::getStatus, "rejected")
                .set(KnowledgeReviewTask::getReviewerId, reviewer)
                .set(KnowledgeReviewTask::getReviewComment, reason)
                .set(KnowledgeReviewTask::getReviewedAt, now));
        if (!updated) throw new ServerException(409, "review task state changed");
        boolean versionUpdated = versionService.update(Wrappers.lambdaUpdate(KnowledgeDocumentVersion.class)
                .eq(KnowledgeDocumentVersion::getId, task.getDocumentVersionId())
                .eq(KnowledgeDocumentVersion::getReviewStatus, KnowledgeReviewStatus.SUBMITTED)
                .set(KnowledgeDocumentVersion::getReviewStatus, KnowledgeReviewStatus.REJECTED)
                .set(KnowledgeDocumentVersion::getReviewedBy, reviewer)
                .set(KnowledgeDocumentVersion::getReviewedAt, now)
                .set(KnowledgeDocumentVersion::getReviewComment, reason));
        if (!versionUpdated) throw new ServerException(409, "document version state changed");
        updateDocumentReviewStatus(task.getDocumentId(), KnowledgeReviewStatus.REJECTED, null, null);
        log(taskId, task.getDocumentId(), task.getDocumentVersionId(), "REJECTED",
                KnowledgeReviewStatus.SUBMITTED, KnowledgeReviewStatus.REJECTED, reason);
    }

    private ApprovalResult approveInTransaction(String taskId, String comment) {
        KnowledgeReviewTask task = requireTask(taskId);
        KnowledgeBase base = accessService.requireApprovable(task.getKnowledgeBaseId());
        String reviewer = accessService.currentAdminId();
        ensureReviewer(task, reviewer);
        if (booleanConfig(base.getReviewConfig(), "requireDifferentApprover", true)
                && reviewer.equals(task.getSubmitterId())) {
            throw new ServerException(403, "submitter cannot approve own document");
        }
        KnowledgeDocumentVersion version = requireVersion(task.getDocumentVersionId());
        KnowledgeDocument document = requireDocument(task.getDocumentId());
        if (!StringUtils.equals(document.getSubmittedVersionId(), version.getId())) {
            throw new ServerException(409, "document submission pointer has changed");
        }
        if (!KnowledgeReviewStatus.SUBMITTED.equals(version.getReviewStatus())
                || !StringUtils.equals(task.getSourceChecksum(), version.getContentChecksum())) {
            throw new ServerException(409, "submitted document content or state changed");
        }
        long now = System.currentTimeMillis();
        boolean taskUpdated = taskService.update(Wrappers.lambdaUpdate(KnowledgeReviewTask.class)
                .eq(KnowledgeReviewTask::getId, taskId)
                .in(KnowledgeReviewTask::getStatus, "pending", "claimed")
                .set(KnowledgeReviewTask::getStatus, "approved")
                .set(KnowledgeReviewTask::getReviewerId, reviewer)
                .set(KnowledgeReviewTask::getReviewComment, comment)
                .set(KnowledgeReviewTask::getReviewedAt, now));
        if (!taskUpdated) throw new ServerException(409, "review task state changed");
        boolean versionUpdated = versionService.update(Wrappers.lambdaUpdate(KnowledgeDocumentVersion.class)
                .eq(KnowledgeDocumentVersion::getId, version.getId())
                .eq(KnowledgeDocumentVersion::getReviewStatus, KnowledgeReviewStatus.SUBMITTED)
                .set(KnowledgeDocumentVersion::getReviewStatus, KnowledgeReviewStatus.APPROVED)
                .set(KnowledgeDocumentVersion::getReviewedBy, reviewer)
                .set(KnowledgeDocumentVersion::getReviewedAt, now)
                .set(KnowledgeDocumentVersion::getReviewComment, comment));
        if (!versionUpdated) throw new ServerException(409, "document version state changed");
        KnowledgeDocument update = new KnowledgeDocument();
        update.setId(document.getId());
        update.setSubmittedVersionId(null);
        update.setReviewStatus(KnowledgeReviewStatus.APPROVED);
        update.setReviewUpdatedAt(now);
        update.setIndexStatus(1);
        updateDocumentPointers(update, true);
        log(taskId, task.getDocumentId(), version.getId(), "APPROVED",
                KnowledgeReviewStatus.SUBMITTED, KnowledgeReviewStatus.APPROVED, comment);
        return new ApprovalResult(documentService.getById(document.getId()), versionService.getById(version.getId()));
    }

    private void ensureReviewer(KnowledgeReviewTask task, String reviewer) {
        if ("claimed".equals(task.getStatus()) && !reviewer.equals(task.getReviewerId())) {
            throw new ServerException(403, "review task is claimed by another administrator");
        }
        if (!"pending".equals(task.getStatus()) && !"claimed".equals(task.getStatus())) {
            throw new ServerException(409, "review task is already completed");
        }
    }

    private void updateDocumentReviewStatus(String documentId, String status,
                                            String draftVersionId, String submittedVersionId) {
        documentService.update(Wrappers.lambdaUpdate(KnowledgeDocument.class)
                .eq(KnowledgeDocument::getId, documentId)
                .set(KnowledgeDocument::getReviewStatus, status)
                .set(KnowledgeDocument::getReviewUpdatedAt, System.currentTimeMillis())
                .set(draftVersionId != null || KnowledgeReviewStatus.SUBMITTED.equals(status),
                        KnowledgeDocument::getDraftVersionId, draftVersionId)
                .set(submittedVersionId != null || !KnowledgeReviewStatus.SUBMITTED.equals(status),
                        KnowledgeDocument::getSubmittedVersionId, submittedVersionId));
    }

    private void dispatchAiReviewAfterCommit(String reviewId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            aiReviewWorker.run(reviewId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                aiReviewWorker.run(reviewId);
            }
        });
    }

    private boolean hasPendingCriticalIssues(String versionId) {
        KnowledgeAiReview latest = aiReviewRecordService.getOne(Wrappers.lambdaQuery(KnowledgeAiReview.class)
                .eq(KnowledgeAiReview::getDocumentVersionId, versionId)
                .eq(KnowledgeAiReview::getStatus, "success")
                .eq(KnowledgeAiReview::getDeleted, false)
                .orderByDesc(KnowledgeAiReview::getCreatedAt)
                .last("LIMIT 1"), false);
        return latest != null && aiReviewIssueService.count(
                Wrappers.lambdaQuery(com.aether.knowledge.entity.KnowledgeAiReviewIssue.class)
                        .eq(com.aether.knowledge.entity.KnowledgeAiReviewIssue::getAiReviewId, latest.getId())
                        .eq(com.aether.knowledge.entity.KnowledgeAiReviewIssue::getSeverity, "critical")
                        .eq(com.aether.knowledge.entity.KnowledgeAiReviewIssue::getHandleStatus, "pending")
                        .eq(com.aether.knowledge.entity.KnowledgeAiReviewIssue::getDeleted, false)) > 0;
    }

    private void updateDocumentPointers(KnowledgeDocument update, boolean includeNullPointers) {
        documentService.update(Wrappers.lambdaUpdate(KnowledgeDocument.class)
                .eq(KnowledgeDocument::getId, update.getId())
                .set(KnowledgeDocument::getReviewStatus, update.getReviewStatus())
                .set(KnowledgeDocument::getReviewUpdatedAt, update.getReviewUpdatedAt())
                .set(update.getIndexStatus() != null, KnowledgeDocument::getIndexStatus, update.getIndexStatus())
                .set(KnowledgeDocument::getDraftVersionId, update.getDraftVersionId())
                .set(includeNullPointers, KnowledgeDocument::getSubmittedVersionId, update.getSubmittedVersionId()));
    }

    private KnowledgeDocumentVersion requireVersion(String id) {
        KnowledgeDocumentVersion version = versionService.getById(id);
        if (version == null || Boolean.TRUE.equals(version.getDeleted())) {
            throw new ServerException(404, "document version not found");
        }
        return version;
    }

    private KnowledgeDocument requireDocument(String id) {
        KnowledgeDocument document = documentService.getById(id);
        if (document == null || Boolean.TRUE.equals(document.getDeleted())) {
            throw new ServerException(404, "knowledge document not found");
        }
        return document;
    }

    private KnowledgeReviewTask requireTask(String id) {
        KnowledgeReviewTask task = taskService.getById(id);
        if (task == null || Boolean.TRUE.equals(task.getDeleted())) {
            throw new ServerException(404, "review task not found");
        }
        return task;
    }

    private boolean booleanConfig(String value, String key, boolean defaultValue) {
        if (StringUtils.isBlank(value)) {
            throw new ServerException(500, "knowledge review configuration is required");
        }
        try {
            Boolean configured = JSONObject.parseObject(value).getBoolean(key);
            return configured == null ? defaultValue : configured;
        } catch (Exception e) {
            throw new ServerException(500, "knowledge review configuration is invalid");
        }
    }

    private String checksum(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(StringUtils.defaultString(content).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception e) {
            throw new ServerException(500, "failed to calculate document checksum");
        }
    }

    private void log(String taskId, String documentId, String versionId, String action,
                     String before, String after, String comment) {
        KnowledgeReviewActionLog entry = new KnowledgeReviewActionLog();
        entry.setReviewTaskId(taskId);
        entry.setDocumentId(documentId);
        entry.setDocumentVersionId(versionId);
        entry.setOperatorId(accessService.currentAdminId());
        entry.setAction(action);
        entry.setBeforeStatus(before);
        entry.setAfterStatus(after);
        entry.setComment(comment);
        actionLogService.save(entry);
    }

    private static class ApprovalResult {
        private final KnowledgeDocument document;
        private final KnowledgeDocumentVersion version;
        private ApprovalResult(KnowledgeDocument document, KnowledgeDocumentVersion version) {
            this.document = document;
            this.version = version;
        }
    }
}
