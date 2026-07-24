package com.aether.knowledge.service.impl;

import com.aether.exception.ServerException;
import com.aether.i18n.I18nService;
import com.aether.i18n.I18nUtils;
import com.aether.knowledge.entity.KnowledgeAiReview;
import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.entity.KnowledgeReviewTask;
import com.aether.knowledge.model.KnowledgeReviewStatus;
import com.aether.knowledge.model.KnowledgeReviewTaskStatus;
import com.aether.knowledge.service.KnowledgeAccessService;
import com.aether.knowledge.service.KnowledgeAiReviewIssueService;
import com.aether.knowledge.service.KnowledgeAiReviewRecordService;
import com.aether.knowledge.service.KnowledgeDocumentIndexService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeDocumentVersionService;
import com.aether.knowledge.service.KnowledgeReviewTaskService;
import com.aether.knowledge.mapper.KnowledgeDocumentMapper;
import com.aether.knowledge.workflow.KnowledgeDocumentWorkflowStateManager;
import com.aether.knowledge.workflow.KnowledgeReviewAuditWriter;
import com.aether.knowledge.workflow.KnowledgeReviewConfigResolver;
import com.aether.knowledge.workflow.TransactionAfterCommitExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentWorkflowServiceImplTest {
    @Mock private KnowledgeDocumentService documentService;
    @Mock private KnowledgeDocumentVersionService versionService;
    @Mock private KnowledgeReviewTaskService taskService;
    @Mock private KnowledgeAiReviewRecordService aiReviewRecordService;
    @Mock private KnowledgeAiReviewIssueService aiReviewIssueService;
    @Mock private KnowledgeAccessService accessService;
    @Mock private KnowledgeDocumentIndexService indexService;
    @Mock private KnowledgeAiReviewWorker aiReviewWorker;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private KnowledgeDocumentMapper documentMapper;
    @Mock private KnowledgeDocumentWorkflowStateManager stateManager;
    @Mock private KnowledgeReviewAuditWriter auditWriter;

    private KnowledgeDocumentWorkflowServiceImpl service;
    private KnowledgeReviewConfigResolver configResolver;
    private TransactionAfterCommitExecutor afterCommitExecutor;

    @BeforeEach
    void setUp() {
        new I18nUtils(org.mockito.Mockito.mock(I18nService.class));
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocumentVersion.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocument.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeAiReview.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeReviewTask.class);
        TableInfoHelper.initTableInfo(assistant, com.aether.knowledge.entity.KnowledgeAiReviewIssue.class);
        configResolver = new KnowledgeReviewConfigResolver();
        afterCommitExecutor = new TransactionAfterCommitExecutor();
        service = new KnowledgeDocumentWorkflowServiceImpl(documentService, versionService, taskService,
                aiReviewRecordService, aiReviewIssueService, accessService, indexService, aiReviewWorker,
                transactionTemplate, documentMapper, stateManager, configResolver, auditWriter,
                afterCommitExecutor);
    }

    @Test
    void startsAiReviewOnlyAfterDraftTransitions() {
        KnowledgeDocumentVersion version = version("version-1", KnowledgeReviewStatus.DRAFT);
        KnowledgeDocument document = document();
        when(versionService.getById("version-1")).thenReturn(version);
        when(documentService.getById("document-1")).thenReturn(document);
        when(versionService.update(any())).thenReturn(true);
        doAnswer(invocation -> {
            KnowledgeAiReview review = invocation.getArgument(0);
            review.setId("review-1");
            return true;
        }).when(aiReviewRecordService).save(any(KnowledgeAiReview.class));

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertEquals("review-1", service.startAiReview("version-1"));
            verifyNoInteractions(aiReviewWorker);
            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(aiReviewWorker).run("review-1");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void refusesSubmissionWhenCriticalAiIssueIsPending() {
        KnowledgeDocumentVersion version = version("version-1", KnowledgeReviewStatus.AI_REVIEWED);
        KnowledgeDocument document = document();
        document.setDraftVersionId("version-1");
        KnowledgeBase base = new KnowledgeBase();
        base.setId("kb-1");
        base.setReviewConfig("{\"blockOnCriticalIssues\":true}");
        when(versionService.getById("version-1")).thenReturn(version);
        when(documentService.getById("document-1")).thenReturn(document);
        when(accessService.requireSubmittable("kb-1")).thenReturn(base);
        KnowledgeAiReview review = new KnowledgeAiReview();
        review.setId("review-1");
        review.setSourceChecksum("checksum");
        when(aiReviewRecordService.getOne(any(), org.mockito.ArgumentMatchers.eq(false))).thenReturn(review);
        when(aiReviewIssueService.count(any())).thenReturn(1L);

        assertThrows(ServerException.class, () -> service.submit("version-1", null));
    }

    @Test
    void editingAnAiReviewedVersionReturnsItToDraftWithChecksumCas() {
        KnowledgeDocumentVersion version = version("version-1", KnowledgeReviewStatus.AI_REVIEWED);
        KnowledgeDocument document = document();
        document.setDraftVersionId("version-1");
        when(versionService.getById("version-1")).thenReturn(version);
        when(documentService.getById("document-1")).thenReturn(document);
        when(versionService.update(any())).thenReturn(true);

        service.updateDraft("version-1", "updated content", "checksum");

        ArgumentCaptor<LambdaUpdateWrapper> versionUpdate = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(versionService).update(versionUpdate.capture());
        assertTrue(versionUpdate.getValue().getParamNameValuePairs().containsValue(KnowledgeReviewStatus.DRAFT));
        assertTrue(versionUpdate.getValue().getSqlSegment().contains("content_checksum"));
    }

    @Test
    void applyingReviewedChangesPreservesAiReviewedStatusWithChecksumCas() {
        KnowledgeDocumentVersion version = version("version-1", KnowledgeReviewStatus.AI_REVIEWED);
        KnowledgeDocument document = document();
        document.setDraftVersionId("version-1");
        when(versionService.getById("version-1")).thenReturn(version);
        when(documentService.getById("document-1")).thenReturn(document);
        when(versionService.update(any())).thenReturn(true);

        service.applyAiReviewedChanges("version-1", "accepted AI changes", "checksum");

        ArgumentCaptor<LambdaUpdateWrapper> versionUpdate = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(versionService).update(versionUpdate.capture());
        assertTrue(versionUpdate.getValue().getParamNameValuePairs()
                .containsValue(KnowledgeReviewStatus.AI_REVIEWED));
        assertTrue(versionUpdate.getValue().getSqlSegment().contains("content_checksum"));
        verify(stateManager).updateActiveDraftStatus("document-1", "version-1",
                KnowledgeReviewStatus.AI_REVIEWED);
    }

    @Test
    void refusesSubmissionWhenAnyAiIssueIsPending() {
        KnowledgeDocumentVersion version = version("version-1", KnowledgeReviewStatus.AI_REVIEWED);
        KnowledgeDocument document = document();
        document.setDraftVersionId("version-1");
        KnowledgeBase base = new KnowledgeBase();
        base.setId("kb-1");
        base.setReviewConfig("{\"aiReviewRequired\":true,\"blockOnCriticalIssues\":false}");
        when(versionService.getById("version-1")).thenReturn(version);
        when(documentService.getById("document-1")).thenReturn(document);
        when(accessService.requireSubmittable("kb-1")).thenReturn(base);
        KnowledgeAiReview review = new KnowledgeAiReview();
        review.setId("review-1");
        review.setSourceChecksum("checksum");
        when(aiReviewRecordService.getOne(any(), org.mockito.ArgumentMatchers.eq(false))).thenReturn(review);
        when(aiReviewIssueService.count(any())).thenReturn(1L);

        assertThrows(ServerException.class, () -> service.submit("version-1", null));
    }

    @Test
    void refusesRequiredAiSubmissionWithoutSuccessfulPrecheck() {
        KnowledgeDocumentVersion version = version("version-1", KnowledgeReviewStatus.AI_REVIEWED);
        KnowledgeDocument document = document();
        document.setDraftVersionId("version-1");
        KnowledgeBase base = new KnowledgeBase();
        base.setId("kb-1");
        base.setReviewConfig("{\"aiReviewRequired\":true}");
        when(versionService.getById("version-1")).thenReturn(version);
        when(documentService.getById("document-1")).thenReturn(document);
        when(accessService.requireSubmittable("kb-1")).thenReturn(base);
        when(aiReviewRecordService.getOne(any(), org.mockito.ArgumentMatchers.eq(false))).thenReturn(null);

        assertThrows(ServerException.class, () -> service.submit("version-1", null));
    }

    @Test
    void submitsRequiredAiVersionAfterAcceptedChangesAlterChecksum() {
        KnowledgeDocumentVersion version = version("version-1", KnowledgeReviewStatus.AI_REVIEWED);
        version.setContentChecksum("edited-checksum");
        KnowledgeDocument document = document();
        document.setDraftVersionId("version-1");
        KnowledgeBase base = new KnowledgeBase();
        base.setId("kb-1");
        base.setReviewConfig("{\"aiReviewRequired\":true}");
        KnowledgeAiReview review = new KnowledgeAiReview();
        review.setId("review-1");
        review.setSourceChecksum("precheck-checksum");
        when(versionService.getById("version-1")).thenReturn(version);
        when(documentService.getById("document-1")).thenReturn(document);
        when(accessService.requireSubmittable("kb-1")).thenReturn(base);
        when(aiReviewRecordService.getOne(any(), org.mockito.ArgumentMatchers.eq(false))).thenReturn(review);
        when(aiReviewIssueService.count(any())).thenReturn(0L);
        when(versionService.update(any())).thenReturn(true);
        when(taskService.save(any(KnowledgeReviewTask.class))).thenReturn(true);

        service.submit("version-1", null);

        verify(taskService).save(any(KnowledgeReviewTask.class));
    }

    @Test
    void submitsRequiredAiVersionAfterMatchingSuccessfulPrecheckWithNoPendingIssues() {
        KnowledgeDocumentVersion version = version("version-1", KnowledgeReviewStatus.AI_REVIEWED);
        KnowledgeDocument document = document();
        document.setDraftVersionId("version-1");
        KnowledgeBase base = new KnowledgeBase();
        base.setId("kb-1");
        base.setReviewConfig("{\"aiReviewRequired\":true}");
        KnowledgeAiReview review = new KnowledgeAiReview();
        review.setId("review-1");
        review.setSourceChecksum("checksum");
        when(versionService.getById("version-1")).thenReturn(version);
        when(documentService.getById("document-1")).thenReturn(document);
        when(accessService.requireSubmittable("kb-1")).thenReturn(base);
        when(aiReviewRecordService.getOne(any(), org.mockito.ArgumentMatchers.eq(false))).thenReturn(review);
        when(aiReviewIssueService.count(any())).thenReturn(0L);
        when(versionService.update(any())).thenReturn(true);
        when(taskService.save(any(KnowledgeReviewTask.class))).thenReturn(true);

        service.submit("version-1", null);

        verify(taskService).save(any(KnowledgeReviewTask.class));
    }

    @Test
    void preventsSubmitterFromClaimingWhenDifferentApproverIsRequired() {
        KnowledgeReviewTask task = new KnowledgeReviewTask();
        task.setId("task-1");
        task.setKnowledgeBaseId("kb-1");
        task.setSubmitterId("admin-1");
        task.setStatus(KnowledgeReviewTaskStatus.PENDING);
        KnowledgeBase base = new KnowledgeBase();
        base.setReviewConfig("{\"requireDifferentApprover\":true}");
        when(taskService.getById("task-1")).thenReturn(task);
        when(accessService.requireApprovable("kb-1")).thenReturn(base);
        when(accessService.currentAdminId()).thenReturn("admin-1");

        assertThrows(ServerException.class, () -> service.claim("task-1"));
    }

    @Test
    void refusesToClaimTaskWhenItIsNoLongerTheActiveSubmission() {
        KnowledgeReviewTask task = reviewTask(KnowledgeReviewTaskStatus.PENDING);
        KnowledgeBase base = new KnowledgeBase();
        base.setReviewConfig("{\"requireDifferentApprover\":true}");
        when(taskService.getById("task-1")).thenReturn(task);
        when(accessService.requireApprovable("kb-1")).thenReturn(base);
        when(accessService.currentAdminId()).thenReturn("admin-2");
        org.mockito.Mockito.doThrow(new ServerException(409, "stale"))
                .when(stateManager).requireActiveSubmission(task);

        assertThrows(ServerException.class, () -> service.claim("task-1"));

        verify(taskService, never()).claim(any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void claimsTaskWhenDocumentVersionAndChecksumStillMatch() {
        KnowledgeReviewTask task = reviewTask(KnowledgeReviewTaskStatus.PENDING);
        KnowledgeBase base = new KnowledgeBase();
        base.setReviewConfig("{\"requireDifferentApprover\":true}");
        when(taskService.getById("task-1")).thenReturn(task);
        when(accessService.requireApprovable("kb-1")).thenReturn(base);
        when(accessService.currentAdminId()).thenReturn("admin-2");
        when(taskService.claim(org.mockito.ArgumentMatchers.eq("task-1"),
                org.mockito.ArgumentMatchers.eq("admin-2"),
                org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);

        service.claim("task-1");

        verify(taskService).claim(org.mockito.ArgumentMatchers.eq("task-1"),
                org.mockito.ArgumentMatchers.eq("admin-2"),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void rollsBackSubmissionWhenReviewTaskCannotBeCreated() {
        KnowledgeDocumentVersion version = version("version-1", KnowledgeReviewStatus.DRAFT);
        KnowledgeDocument document = document();
        document.setDraftVersionId("version-1");
        KnowledgeBase base = new KnowledgeBase();
        base.setReviewConfig("{\"aiReviewRequired\":false}");
        when(versionService.getById("version-1")).thenReturn(version);
        when(documentService.getById("document-1")).thenReturn(document);
        when(accessService.requireSubmittable("kb-1")).thenReturn(base);
        when(versionService.update(any())).thenReturn(true);
        when(taskService.save(any(KnowledgeReviewTask.class))).thenReturn(false);

        assertThrows(ServerException.class, () -> service.submit("version-1", null));

        verify(documentService, never()).update(any());
    }

    @Test
    void requiresClaimBeforeRejecting() {
        KnowledgeReviewTask task = new KnowledgeReviewTask();
        task.setId("task-1");
        task.setKnowledgeBaseId("kb-1");
        task.setStatus(KnowledgeReviewTaskStatus.PENDING);
        when(taskService.getById("task-1")).thenReturn(task);
        when(accessService.currentAdminId()).thenReturn("admin-2");

        assertThrows(ServerException.class, () -> service.reject("task-1", "needs work"));
    }

    @Test
    void rollsBackApprovalTransactionWhenIndexQueueFails() {
        PlatformTransactionManager transactionManager =
                org.mockito.Mockito.mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus =
                org.mockito.Mockito.mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        TransactionTemplate realTransactionTemplate =
                new TransactionTemplate(transactionManager);
        KnowledgeDocumentWorkflowServiceImpl transactionalService =
                new KnowledgeDocumentWorkflowServiceImpl(
                        documentService, versionService, taskService, aiReviewRecordService,
                        aiReviewIssueService, accessService, indexService, aiReviewWorker,
                        realTransactionTemplate, documentMapper, stateManager, configResolver,
                        auditWriter, afterCommitExecutor);
        KnowledgeReviewTask task = reviewTask(KnowledgeReviewTaskStatus.CLAIMED);
        task.setReviewerId("admin-2");
        KnowledgeBase base = new KnowledgeBase();
        base.setReviewConfig("{\"requireDifferentApprover\":true}");
        KnowledgeDocument document = document();
        KnowledgeDocumentVersion version =
                version("version-1", KnowledgeReviewStatus.SUBMITTED);
        KnowledgeDocumentWorkflowStateManager.ActiveSubmission submission =
                new KnowledgeDocumentWorkflowStateManager.ActiveSubmission(document, version);
        when(taskService.getById("task-1")).thenReturn(task);
        when(accessService.requireApprovable("kb-1")).thenReturn(base);
        when(accessService.currentAdminId()).thenReturn("admin-2");
        when(stateManager.requireActiveSubmission(task)).thenReturn(submission);
        when(taskService.update(any())).thenReturn(true);
        when(versionService.update(any())).thenReturn(true);
        when(documentService.getById("document-1")).thenReturn(document);
        when(versionService.getById("version-1")).thenReturn(version);
        when(indexService.queueReindex(document, version, "approved"))
                .thenThrow(new ServerException(500, "index queue failed"));

        assertThrows(ServerException.class,
                () -> transactionalService.approve("task-1", "looks good"));

        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(any());
    }

    private KnowledgeDocument document() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId("document-1");
        document.setKnowledgeBaseId("kb-1");
        return document;
    }

    private KnowledgeDocumentVersion version(String id, String status) {
        KnowledgeDocumentVersion version = new KnowledgeDocumentVersion();
        version.setId(id);
        version.setKnowledgeDocumentId("document-1");
        version.setContentChecksum("checksum");
        version.setReviewStatus(status);
        return version;
    }

    private KnowledgeReviewTask reviewTask(String status) {
        KnowledgeReviewTask task = new KnowledgeReviewTask();
        task.setId("task-1");
        task.setKnowledgeBaseId("kb-1");
        task.setDocumentId("document-1");
        task.setDocumentVersionId("version-1");
        task.setSubmitterId("admin-1");
        task.setSourceChecksum("checksum");
        task.setStatus(status);
        return task;
    }
}
