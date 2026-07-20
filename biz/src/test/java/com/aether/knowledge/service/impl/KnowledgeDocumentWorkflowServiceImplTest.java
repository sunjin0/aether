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
import com.aether.knowledge.service.KnowledgeAccessService;
import com.aether.knowledge.service.KnowledgeAiReviewIssueService;
import com.aether.knowledge.service.KnowledgeAiReviewRecordService;
import com.aether.knowledge.service.KnowledgeDocumentIndexService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeDocumentVersionService;
import com.aether.knowledge.service.KnowledgeReviewActionLogService;
import com.aether.knowledge.service.KnowledgeReviewTaskService;
import com.aether.knowledge.mapper.KnowledgeDocumentMapper;
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
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentWorkflowServiceImplTest {
    @Mock private KnowledgeDocumentService documentService;
    @Mock private KnowledgeDocumentVersionService versionService;
    @Mock private KnowledgeReviewTaskService taskService;
    @Mock private KnowledgeReviewActionLogService actionLogService;
    @Mock private KnowledgeAiReviewRecordService aiReviewRecordService;
    @Mock private KnowledgeAiReviewIssueService aiReviewIssueService;
    @Mock private KnowledgeAccessService accessService;
    @Mock private KnowledgeDocumentIndexService indexService;
    @Mock private KnowledgeAiReviewWorker aiReviewWorker;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private KnowledgeDocumentMapper documentMapper;

    private KnowledgeDocumentWorkflowServiceImpl service;

    @BeforeEach
    void setUp() {
        new I18nUtils(org.mockito.Mockito.mock(I18nService.class));
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocumentVersion.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocument.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeAiReview.class);
        TableInfoHelper.initTableInfo(assistant, com.aether.knowledge.entity.KnowledgeAiReviewIssue.class);
        service = new KnowledgeDocumentWorkflowServiceImpl(documentService, versionService, taskService,
                actionLogService, aiReviewRecordService, aiReviewIssueService, accessService,
                indexService, aiReviewWorker, transactionTemplate, documentMapper);
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
        when(aiReviewRecordService.getOne(any(), org.mockito.ArgumentMatchers.eq(false))).thenReturn(review);
        when(aiReviewIssueService.count(any())).thenReturn(1L);

        assertThrows(ServerException.class, () -> service.submit("version-1", null));
    }

    @Test
    void editingAnAiReviewedVersionKeepsItAiReviewed() {
        KnowledgeDocumentVersion version = version("version-1", KnowledgeReviewStatus.AI_REVIEWED);
        KnowledgeDocument document = document();
        document.setDraftVersionId("version-1");
        when(versionService.getById("version-1")).thenReturn(version);
        when(documentService.getById("document-1")).thenReturn(document);
        when(versionService.update(any())).thenReturn(true);

        service.updateDraft("version-1", "updated content", "checksum");

        ArgumentCaptor<LambdaUpdateWrapper> versionUpdate = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(versionService).update(versionUpdate.capture());
        assertTrue(versionUpdate.getValue().getParamNameValuePairs().containsValue(KnowledgeReviewStatus.AI_REVIEWED));
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
    void submitsRequiredAiVersionAfterSuccessfulPrecheckWithNoPendingIssues() {
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
}
