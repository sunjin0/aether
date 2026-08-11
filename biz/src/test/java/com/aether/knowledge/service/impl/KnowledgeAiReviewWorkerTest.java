package com.aether.knowledge.service.impl;

import com.aether.agent.model.ModelClientFactory;
import com.aether.agent.service.ModelProviderService;
import com.aether.agent.service.ModelCatalogService;
import com.aether.i18n.I18nService;
import com.aether.i18n.I18nUtils;
import com.aether.knowledge.entity.KnowledgeAiReview;
import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.model.KnowledgeAiReviewStatus;
import com.aether.knowledge.model.KnowledgeReviewStatus;
import com.aether.knowledge.service.KnowledgeAiReviewIssueService;
import com.aether.knowledge.service.KnowledgeAiReviewRecordService;
import com.aether.knowledge.service.KnowledgeBaseService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeDocumentVersionService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeAiReviewWorkerTest {
    @Mock private KnowledgeAiReviewRecordService reviewService;
    @Mock private KnowledgeAiReviewIssueService issueService;
    @Mock private KnowledgeDocumentVersionService versionService;
    @Mock private KnowledgeDocumentService documentService;
    @Mock private KnowledgeBaseService baseService;
    @Mock private ModelProviderService providerService;
    @Mock private ModelCatalogService modelCatalogService;
    @Mock private ModelClientFactory clientFactory;
    @Mock private ObjectProvider<KnowledgeAiReviewWorker> selfProvider;
    @Mock private TransactionTemplate transactionTemplate;

    private KnowledgeAiReviewWorker worker;
    private KnowledgeAiReview review;
    private KnowledgeDocumentVersion version;
    private KnowledgeDocument document;

    @BeforeEach
    void setUp() {
        new I18nUtils(org.mockito.Mockito.mock(I18nService.class));
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, KnowledgeAiReview.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocumentVersion.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocument.class);
        worker = new KnowledgeAiReviewWorker(reviewService, issueService, versionService,
                documentService, baseService, providerService, modelCatalogService, clientFactory, selfProvider,
                transactionTemplate);
        review = new KnowledgeAiReview();
        review.setId("review-1");
        review.setDocumentVersionId("version-1");
        review.setDocumentId("document-1");
        review.setKnowledgeBaseId("kb-1");
        review.setStatus(KnowledgeAiReviewStatus.RUNNING);
        review.setStartedAt(100L);
        review.setSourceChecksum("checksum");
        version = new KnowledgeDocumentVersion();
        version.setId("version-1");
        version.setKnowledgeDocumentId("document-1");
        version.setContentChecksum("checksum");
        version.setReviewStatus(KnowledgeReviewStatus.AI_REVIEWING);
        document = new KnowledgeDocument();
        document.setId("document-1");
        document.setKnowledgeBaseId("kb-1");
        document.setDraftVersionId("version-1");
    }

    @Test
    void expiredWorkerFailureDoesNotResetStateOwnedByNewLease() {
        KnowledgeBase base = new KnowledgeBase();
        base.setId("kb-1");
        when(reviewService.update(any())).thenReturn(true, false);
        when(reviewService.getById("review-1")).thenReturn(review);
        when(versionService.getById("version-1")).thenReturn(version);
        when(documentService.getById("document-1")).thenReturn(document);
        when(baseService.getById("kb-1")).thenReturn(base);

        worker.run("review-1");

        verify(versionService, never()).update(any());
        verify(documentService, never()).update(any());
    }

    @Test
    void expiredWorkerStaleResultDoesNotResetStateOwnedByNewLease() {
        version.setContentChecksum("new-checksum");
        when(reviewService.update(any())).thenReturn(true, false);
        when(reviewService.getById("review-1")).thenReturn(review);
        when(versionService.getById("version-1")).thenReturn(version);
        when(documentService.getById("document-1")).thenReturn(document);
        when(baseService.getById("kb-1")).thenReturn(new KnowledgeBase());

        worker.run("review-1");

        verify(versionService, never()).update(any());
        verify(documentService, never()).update(any());
    }
}
