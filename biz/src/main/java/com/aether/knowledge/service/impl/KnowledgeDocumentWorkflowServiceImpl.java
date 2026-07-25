package com.aether.knowledge.service.impl;

import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.knowledge.entity.KnowledgeAiReview;
import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.entity.KnowledgeReviewTask;
import com.aether.knowledge.mapper.KnowledgeDocumentMapper;
import com.aether.knowledge.model.KnowledgeJobType;
import com.aether.knowledge.model.KnowledgeReviewAction;
import com.aether.knowledge.model.KnowledgeReviewStatus;
import com.aether.knowledge.model.KnowledgeReviewTaskStatus;
import com.aether.knowledge.model.KnowledgeAiReviewStatus;
import com.aether.knowledge.model.KnowledgeAiReviewIssueStatus;
import com.aether.knowledge.service.KnowledgeAccessService;
import com.aether.knowledge.service.KnowledgeAiReviewRecordService;
import com.aether.knowledge.service.KnowledgeAiReviewIssueService;
import com.aether.knowledge.service.KnowledgeDocumentIndexService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeDocumentVersionService;
import com.aether.knowledge.service.KnowledgeDocumentWorkflowService;
import com.aether.knowledge.service.KnowledgeReviewTaskService;
import com.aether.knowledge.workflow.KnowledgeDocumentWorkflowStateManager;
import com.aether.knowledge.workflow.KnowledgeReviewAuditWriter;
import com.aether.knowledge.workflow.KnowledgeReviewConfigResolver;
import com.aether.knowledge.workflow.TransactionAfterCommitExecutor;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class KnowledgeDocumentWorkflowServiceImpl implements KnowledgeDocumentWorkflowService {
    private final KnowledgeDocumentService documentService;
    private final KnowledgeDocumentVersionService versionService;
    private final KnowledgeReviewTaskService taskService;
    private final KnowledgeAiReviewRecordService aiReviewRecordService;
    private final KnowledgeAiReviewIssueService aiReviewIssueService;
    private final KnowledgeAccessService accessService;
    private final KnowledgeDocumentIndexService indexService;
    private final KnowledgeAiReviewWorker aiReviewWorker;
    private final TransactionTemplate transactionTemplate;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeDocumentWorkflowStateManager stateManager;
    private final KnowledgeReviewConfigResolver configResolver;
    private final KnowledgeReviewAuditWriter auditWriter;
    private final TransactionAfterCommitExecutor afterCommitExecutor;

    public KnowledgeDocumentWorkflowServiceImpl(KnowledgeDocumentService documentService,
                                                KnowledgeDocumentVersionService versionService,
                                                KnowledgeReviewTaskService taskService,
                                                KnowledgeAiReviewRecordService aiReviewRecordService,
                                                KnowledgeAiReviewIssueService aiReviewIssueService,
                                                KnowledgeAccessService accessService,
                                                KnowledgeDocumentIndexService indexService,
                                                KnowledgeAiReviewWorker aiReviewWorker,
                                                TransactionTemplate transactionTemplate,
                                                KnowledgeDocumentMapper documentMapper,
                                                KnowledgeDocumentWorkflowStateManager stateManager,
                                                KnowledgeReviewConfigResolver configResolver,
                                                KnowledgeReviewAuditWriter auditWriter,
                                                TransactionAfterCommitExecutor afterCommitExecutor) {
        this.documentService = documentService;
        this.versionService = versionService;
        this.taskService = taskService;
        this.aiReviewRecordService = aiReviewRecordService;
        this.aiReviewIssueService = aiReviewIssueService;
        this.accessService = accessService;
        this.indexService = indexService;
        this.aiReviewWorker = aiReviewWorker;
        this.transactionTemplate = transactionTemplate;
        this.documentMapper = documentMapper;
        this.stateManager = stateManager;
        this.configResolver = configResolver;
        this.auditWriter = auditWriter;
        this.afterCommitExecutor = afterCommitExecutor;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentVersion createDraft(KnowledgeDocument document, String sourceVersionId) {
        if (document == null || StringUtils.isBlank(document.getId())) {
            throw new ServerException(400, I18nUtils.getMessage("knowledge.document.required"));
        }
        KnowledgeDocument lockedDocument = documentMapper.selectActiveForUpdate(document.getId());
        if (lockedDocument == null) throw new ServerException(404, I18nUtils.getMessage("knowledge.document.not-found"));
        accessService.requireWritable(lockedDocument.getKnowledgeBaseId());
        if (StringUtils.isNotBlank(lockedDocument.getDraftVersionId())
                || StringUtils.isNotBlank(lockedDocument.getSubmittedVersionId())) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.document.draft-or-review.active"));
        }
        KnowledgeDocumentVersion version = versionService.createNextVersion(document);
        version.setOriginalContent(document.getContent());
        version.setContentChecksum(checksum(document.getContent()));
        version.setReviewStatus(KnowledgeReviewStatus.DRAFT);
        version.setSourceVersionId(sourceVersionId);
        version.setParserType(document.getParserType());
        if (!versionService.updateById(version)) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.document-version.state.changed"));
        }

        KnowledgeDocument update = new KnowledgeDocument();
        update.setId(document.getId());
        update.setDraftVersionId(version.getId());
        update.setSubmittedVersionId(null);
        update.setReviewStatus(KnowledgeReviewStatus.DRAFT);
        update.setReviewUpdatedAt(System.currentTimeMillis());
        updateDocumentPointers(update, true);
        auditWriter.write(accessService.currentAdminId(), null, document.getId(), version.getId(),
                "DRAFT_CREATED", null,
                KnowledgeReviewStatus.DRAFT, null);
        return version;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentVersion updateDraft(String versionId, String content, String expectedChecksum) {
        return updateDraft(versionId, content, expectedChecksum, KnowledgeReviewStatus.DRAFT);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentVersion applyAiReviewedChanges(String versionId, String content,
                                                            String expectedChecksum) {
        return updateDraft(versionId, content, expectedChecksum, KnowledgeReviewStatus.AI_REVIEWED);
    }

    private KnowledgeDocumentVersion updateDraft(String versionId, String content,
                                                  String expectedChecksum, String nextReviewStatus) {
        KnowledgeDocumentVersion version = requireVersion(versionId);
        KnowledgeDocument document = requireDocument(version.getKnowledgeDocumentId());
        accessService.requireWritable(document.getKnowledgeBaseId());
        if (!StringUtils.equals(document.getDraftVersionId(), versionId)) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.document.draft-pointer.changed"));
        }
        boolean preservingAiReview = KnowledgeReviewStatus.AI_REVIEWED.equals(nextReviewStatus);
        if ((preservingAiReview && !KnowledgeReviewStatus.AI_REVIEWED.equals(version.getReviewStatus()))
                || (!preservingAiReview
                && !KnowledgeReviewStatus.DRAFT.equals(version.getReviewStatus())
                && !KnowledgeReviewStatus.AI_REVIEWED.equals(version.getReviewStatus()))) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.document-version.edit.invalid-state"));
        }
        if (StringUtils.isBlank(expectedChecksum)) {
            throw new ServerException(400, I18nUtils.getMessage("knowledge.document.draft-checksum.required"));
        }
        if (!StringUtils.equals(expectedChecksum, version.getContentChecksum())) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.document.draft.modified"));
        }
        String newChecksum = checksum(content);
        boolean updated = versionService.update(Wrappers.lambdaUpdate(KnowledgeDocumentVersion.class)
                .eq(KnowledgeDocumentVersion::getId, versionId)
                .eq(KnowledgeDocumentVersion::getContentChecksum, expectedChecksum)
                .in(!preservingAiReview, KnowledgeDocumentVersion::getReviewStatus,
                        KnowledgeReviewStatus.DRAFT, KnowledgeReviewStatus.AI_REVIEWED)
                .eq(preservingAiReview, KnowledgeDocumentVersion::getReviewStatus,
                        KnowledgeReviewStatus.AI_REVIEWED)
                .set(KnowledgeDocumentVersion::getContent, content)
                .set(KnowledgeDocumentVersion::getStructuredContent, null)
                .set(KnowledgeDocumentVersion::getContentChecksum, newChecksum)
                .set(KnowledgeDocumentVersion::getReviewStatus, nextReviewStatus));
        if (!updated) throw new ServerException(409, I18nUtils.getMessage("knowledge.document.draft-state.changed"));
        stateManager.updateActiveDraftStatus(document.getId(), versionId, nextReviewStatus);
        auditWriter.write(accessService.currentAdminId(), null, document.getId(), versionId,
                "DRAFT_UPDATED", version.getReviewStatus(),
                nextReviewStatus, null);
        return versionService.getById(versionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String startAiReview(String versionId) {
        KnowledgeDocumentVersion version = requireVersion(versionId);
        KnowledgeDocument document = requireDocument(version.getKnowledgeDocumentId());
        accessService.requireWritable(document.getKnowledgeBaseId());
        if (!KnowledgeReviewStatus.DRAFT.equals(version.getReviewStatus())) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.start.invalid-state"));
        }
        KnowledgeAiReview review = new KnowledgeAiReview();
        review.setKnowledgeBaseId(document.getKnowledgeBaseId());
        review.setDocumentId(document.getId());
        review.setDocumentVersionId(versionId);
        review.setSourceChecksum(version.getContentChecksum());
        review.setSourceContent(version.getContent());
        review.setPromptVersion("v1");
        review.setStatus(KnowledgeAiReviewStatus.PENDING);
        if (!aiReviewRecordService.save(review)) {
            throw new ServerException(500, I18nUtils.getMessage("knowledge.ai-review.create.failed"));
        }
        boolean transitioned = versionService.update(Wrappers.lambdaUpdate(KnowledgeDocumentVersion.class)
                .eq(KnowledgeDocumentVersion::getId, versionId)
                .eq(KnowledgeDocumentVersion::getReviewStatus, KnowledgeReviewStatus.DRAFT)
                .set(KnowledgeDocumentVersion::getReviewStatus, KnowledgeReviewStatus.AI_REVIEWING));
        if (!transitioned) throw new ServerException(409, I18nUtils.getMessage("knowledge.document.draft-state.changed"));
        stateManager.updateActiveDraftStatus(document.getId(), versionId,
                KnowledgeReviewStatus.AI_REVIEWING);
        auditWriter.write(accessService.currentAdminId(), null, document.getId(), versionId,
                "AI_REVIEW_STARTED",
                KnowledgeReviewStatus.DRAFT,
                KnowledgeReviewStatus.AI_REVIEWING, null);
        afterCommitExecutor.execute(() -> aiReviewWorker.run(review.getId()));
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
            throw new ServerException(409, I18nUtils.getMessage("knowledge.document.draft-pointer.changed"));
        }
        boolean aiRequired = configResolver.isAiReviewRequired(base.getReviewConfig());
        KnowledgeAiReview latestSuccessfulReview = latestSuccessfulAiReview(versionId);
        if (aiRequired && (!KnowledgeReviewStatus.AI_REVIEWED.equals(version.getReviewStatus())
                || latestSuccessfulReview == null)) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.required-before-submission"));
        }
        if (!KnowledgeReviewStatus.DRAFT.equals(version.getReviewStatus())
                && !KnowledgeReviewStatus.AI_REVIEWED.equals(version.getReviewStatus())) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.document-version.submit.invalid-state"));
        }
        if (aiRequired && hasPendingAiIssues(latestSuccessfulReview)) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.issues.pending"));
        }
        String submitter = accessService.currentAdminId();
        long now = System.currentTimeMillis();
        boolean transitioned = versionService.update(Wrappers.lambdaUpdate(KnowledgeDocumentVersion.class)
                .eq(KnowledgeDocumentVersion::getId, versionId)
                .eq(KnowledgeDocumentVersion::getContentChecksum, version.getContentChecksum())
                .in(KnowledgeDocumentVersion::getReviewStatus,
                        KnowledgeReviewStatus.DRAFT, KnowledgeReviewStatus.AI_REVIEWED)
                .set(KnowledgeDocumentVersion::getReviewStatus, KnowledgeReviewStatus.SUBMITTED)
                .set(KnowledgeDocumentVersion::getSubmittedBy, submitter)
                .set(KnowledgeDocumentVersion::getSubmittedAt, now));
        if (!transitioned) throw new ServerException(409, I18nUtils.getMessage("knowledge.document-version.state.changed"));
        KnowledgeReviewTask task = new KnowledgeReviewTask();
        task.setKnowledgeBaseId(document.getKnowledgeBaseId());
        task.setDocumentId(document.getId());
        task.setDocumentVersionId(versionId);
        task.setSubmitterId(submitter);
        task.setStatus(KnowledgeReviewTaskStatus.PENDING);
        task.setSourceChecksum(version.getContentChecksum());
        task.setSubmitComment(comment);
        task.setSubmittedAt(now);
        if (!taskService.save(task)) {
            throw new ServerException(500, I18nUtils.getMessage("knowledge.review-task.create.failed"));
        }
        stateManager.markSubmitted(document.getId(), versionId, now);
        auditWriter.write(submitter, task.getId(), document.getId(), versionId, KnowledgeReviewAction.SUBMITTED,
                version.getReviewStatus(),
                KnowledgeReviewStatus.SUBMITTED, comment);
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void claim(String taskId) {
        KnowledgeReviewTask task = requireTask(taskId);
        KnowledgeBase base = accessService.requireApprovable(task.getKnowledgeBaseId());
        String reviewer = accessService.currentAdminId();
        if (configResolver.isDifferentApproverRequired(base.getReviewConfig())
                && reviewer.equals(task.getSubmitterId())) {
            throw new ServerException(403, I18nUtils.getMessage("knowledge.document.self-approval.forbidden"));
        }
        stateManager.requireActiveSubmission(task);
        if (!taskService.claim(taskId, reviewer, System.currentTimeMillis())) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.review-task.already-claimed"));
        }
        auditWriter.write(reviewer, taskId, task.getDocumentId(), task.getDocumentVersionId(),
                KnowledgeReviewAction.CLAIMED,
                task.getStatus(), KnowledgeReviewTaskStatus.CLAIMED, null);
    }

    @Override
    public String approve(String taskId, String comment) {
        String jobId = transactionTemplate.execute(status -> {
            ApprovalResult approved = approveInTransaction(taskId, comment);
            return indexService.queueReindex(approved.document, approved.version, KnowledgeJobType.UPLOAD);
        });
        if (jobId == null) throw new ServerException(500, I18nUtils.getMessage("knowledge.document.approve.failed"));
        return jobId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(String taskId, String reason) {
        if (StringUtils.isBlank(reason)) throw new ServerException(400, I18nUtils.getMessage("knowledge.review-task.rejection-reason.required"));
        KnowledgeReviewTask task = requireTask(taskId);
        accessService.requireApprovable(task.getKnowledgeBaseId());
        String reviewer = accessService.currentAdminId();
        ensureReviewer(task, reviewer);
        KnowledgeDocumentWorkflowStateManager.ActiveSubmission submission =
                stateManager.requireActiveSubmission(task);
        long now = System.currentTimeMillis();
        boolean updated = taskService.update(Wrappers.lambdaUpdate(KnowledgeReviewTask.class)
                .eq(KnowledgeReviewTask::getId, taskId)
                .eq(KnowledgeReviewTask::getStatus, KnowledgeReviewTaskStatus.CLAIMED)
                .eq(KnowledgeReviewTask::getReviewerId, reviewer)
                .set(KnowledgeReviewTask::getStatus, KnowledgeReviewTaskStatus.REJECTED)
                .set(KnowledgeReviewTask::getReviewerId, reviewer)
                .set(KnowledgeReviewTask::getReviewComment, reason)
                .set(KnowledgeReviewTask::getReviewedAt, now));
        if (!updated) throw new ServerException(409, I18nUtils.getMessage("knowledge.review-task.state.changed"));
        boolean versionUpdated = versionService.update(Wrappers.lambdaUpdate(KnowledgeDocumentVersion.class)
                .eq(KnowledgeDocumentVersion::getId, task.getDocumentVersionId())
                .eq(KnowledgeDocumentVersion::getReviewStatus, KnowledgeReviewStatus.SUBMITTED)
                .eq(KnowledgeDocumentVersion::getContentChecksum, task.getSourceChecksum())
                .set(KnowledgeDocumentVersion::getReviewStatus, KnowledgeReviewStatus.REJECTED)
                .set(KnowledgeDocumentVersion::getReviewedBy, reviewer)
                .set(KnowledgeDocumentVersion::getReviewedAt, now)
                .set(KnowledgeDocumentVersion::getReviewComment, reason));
        if (!versionUpdated) throw new ServerException(409, I18nUtils.getMessage("knowledge.document-version.state.changed"));
        stateManager.finishSubmission(submission.getDocument().getId(),
                submission.getVersion().getId(),
                KnowledgeReviewStatus.REJECTED, now, null);
        auditWriter.write(reviewer, taskId, task.getDocumentId(), task.getDocumentVersionId(),
                KnowledgeReviewAction.REJECTED,
                KnowledgeReviewStatus.SUBMITTED, KnowledgeReviewStatus.REJECTED, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentVersion editReviewContent(String taskId, String content, String expectedChecksum) {
        KnowledgeReviewTask task = requireTask(taskId);
        KnowledgeBase base = accessService.requireApprovable(task.getKnowledgeBaseId());
        String reviewer = accessService.currentAdminId();
        ensureReviewer(task, reviewer);
        if (StringUtils.isBlank(expectedChecksum)) {
            throw new ServerException(400, I18nUtils.getMessage("knowledge.document.draft-checksum.required"));
        }
        KnowledgeDocumentVersion version = requireVersion(task.getDocumentVersionId());
        if (!KnowledgeReviewStatus.SUBMITTED.equals(version.getReviewStatus())) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.document-version.edit.invalid-state"));
        }
        if (!StringUtils.equals(expectedChecksum, version.getContentChecksum())) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.document.draft.modified"));
        }
        String newChecksum = checksum(content);
        long now = System.currentTimeMillis();
        boolean versionUpdated = versionService.update(Wrappers.lambdaUpdate(KnowledgeDocumentVersion.class)
                .eq(KnowledgeDocumentVersion::getId, version.getId())
                .eq(KnowledgeDocumentVersion::getReviewStatus, KnowledgeReviewStatus.SUBMITTED)
                .eq(KnowledgeDocumentVersion::getContentChecksum, expectedChecksum)
                .set(KnowledgeDocumentVersion::getContent, content)
                .set(KnowledgeDocumentVersion::getStructuredContent, null)
                .set(KnowledgeDocumentVersion::getContentChecksum, newChecksum));
        if (!versionUpdated) throw new ServerException(409, I18nUtils.getMessage("knowledge.document.draft-state.changed"));
        boolean taskUpdated = taskService.update(Wrappers.lambdaUpdate(KnowledgeReviewTask.class)
                .eq(KnowledgeReviewTask::getId, taskId)
                .eq(KnowledgeReviewTask::getStatus, KnowledgeReviewTaskStatus.CLAIMED)
                .eq(KnowledgeReviewTask::getReviewerId, reviewer)
                .set(KnowledgeReviewTask::getSourceChecksum, newChecksum));
        if (!taskUpdated) throw new ServerException(409, I18nUtils.getMessage("knowledge.review-task.state.changed"));
        auditWriter.write(reviewer, taskId, task.getDocumentId(), version.getId(), KnowledgeReviewAction.EDITED,
                KnowledgeReviewStatus.SUBMITTED, KnowledgeReviewStatus.SUBMITTED, null);
        return versionService.getById(version.getId());
    }

    private ApprovalResult approveInTransaction(String taskId, String comment) {
        KnowledgeReviewTask task = requireTask(taskId);
        KnowledgeBase base = accessService.requireApprovable(task.getKnowledgeBaseId());
        String reviewer = accessService.currentAdminId();
        ensureReviewer(task, reviewer);
        if (configResolver.isDifferentApproverRequired(base.getReviewConfig())
                && reviewer.equals(task.getSubmitterId())) {
            throw new ServerException(403, I18nUtils.getMessage("knowledge.document.self-approval.forbidden"));
        }
        KnowledgeDocumentWorkflowStateManager.ActiveSubmission submission =
                stateManager.requireActiveSubmission(task);
        KnowledgeDocumentVersion version = submission.getVersion();
        KnowledgeDocument document = submission.getDocument();
        long now = System.currentTimeMillis();
        boolean taskUpdated = taskService.update(Wrappers.lambdaUpdate(KnowledgeReviewTask.class)
                .eq(KnowledgeReviewTask::getId, taskId)
                .eq(KnowledgeReviewTask::getStatus, KnowledgeReviewTaskStatus.CLAIMED)
                .eq(KnowledgeReviewTask::getReviewerId, reviewer)
                .set(KnowledgeReviewTask::getStatus, KnowledgeReviewTaskStatus.APPROVED)
                .set(KnowledgeReviewTask::getReviewerId, reviewer)
                .set(KnowledgeReviewTask::getReviewComment, comment)
                .set(KnowledgeReviewTask::getReviewedAt, now));
        if (!taskUpdated) throw new ServerException(409, I18nUtils.getMessage("knowledge.review-task.state.changed"));
        boolean versionUpdated = versionService.update(Wrappers.lambdaUpdate(KnowledgeDocumentVersion.class)
                .eq(KnowledgeDocumentVersion::getId, version.getId())
                .eq(KnowledgeDocumentVersion::getReviewStatus, KnowledgeReviewStatus.SUBMITTED)
                .eq(KnowledgeDocumentVersion::getContentChecksum, task.getSourceChecksum())
                .set(KnowledgeDocumentVersion::getReviewStatus, KnowledgeReviewStatus.APPROVED)
                .set(KnowledgeDocumentVersion::getReviewedBy, reviewer)
                .set(KnowledgeDocumentVersion::getReviewedAt, now)
                .set(KnowledgeDocumentVersion::getReviewComment, comment));
        if (!versionUpdated) throw new ServerException(409, I18nUtils.getMessage("knowledge.document-version.state.changed"));
        stateManager.finishSubmission(document.getId(), version.getId(),
                KnowledgeReviewStatus.APPROVED, now, 1);
        auditWriter.write(reviewer, taskId, task.getDocumentId(), version.getId(), KnowledgeReviewAction.APPROVED,
                KnowledgeReviewStatus.SUBMITTED, KnowledgeReviewStatus.APPROVED, comment);
        return new ApprovalResult(documentService.getById(document.getId()), versionService.getById(version.getId()));
    }

    private void ensureReviewer(KnowledgeReviewTask task, String reviewer) {
        if (KnowledgeReviewTaskStatus.PENDING.equals(task.getStatus())) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.review-task.claim-required"));
        }
        if (!KnowledgeReviewTaskStatus.CLAIMED.equals(task.getStatus())) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.review-task.already-completed"));
        }
        if (!reviewer.equals(task.getReviewerId())) {
            throw new ServerException(403, I18nUtils.getMessage("knowledge.review-task.claimed-by-another-admin"));
        }
    }

    private KnowledgeAiReview latestSuccessfulAiReview(String versionId) {
        return aiReviewRecordService.getOne(Wrappers.lambdaQuery(KnowledgeAiReview.class)
                .eq(KnowledgeAiReview::getDocumentVersionId, versionId)
                .eq(KnowledgeAiReview::getStatus, KnowledgeAiReviewStatus.SUCCESS)
                .eq(KnowledgeAiReview::getDeleted, false)
                .orderByDesc(KnowledgeAiReview::getCreatedAt)
                .last("LIMIT 1"), false);
    }

    private boolean hasPendingAiIssues(KnowledgeAiReview review) {
        return review != null && aiReviewIssueService.count(
                Wrappers.lambdaQuery(com.aether.knowledge.entity.KnowledgeAiReviewIssue.class)
                        .eq(com.aether.knowledge.entity.KnowledgeAiReviewIssue::getAiReviewId, review.getId())
                        .eq(com.aether.knowledge.entity.KnowledgeAiReviewIssue::getHandleStatus,
                                KnowledgeAiReviewIssueStatus.PENDING)
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
            throw new ServerException(404, I18nUtils.getMessage("knowledge.document-version.not-found"));
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

    private KnowledgeReviewTask requireTask(String id) {
        KnowledgeReviewTask task = taskService.getById(id);
        if (task == null || Boolean.TRUE.equals(task.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("knowledge.review-task.not-found"));
        }
        return task;
    }

    private String checksum(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(StringUtils.defaultString(content).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception e) {
            throw new ServerException(500, I18nUtils.getMessage("knowledge.document.checksum.failed"));
        }
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
