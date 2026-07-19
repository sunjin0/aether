package com.aether.knowledge.controller;

import com.aether.i18n.I18nService;
import com.aether.i18n.I18nUtils;
import com.aether.knowledge.entity.KnowledgeAiReview;
import com.aether.knowledge.entity.KnowledgeAiReviewIssue;
import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.entity.KnowledgeReviewActionLog;
import com.aether.knowledge.entity.KnowledgeReviewTask;
import com.aether.knowledge.service.KnowledgeAccessService;
import com.aether.knowledge.service.KnowledgeAiReviewIssueService;
import com.aether.knowledge.service.KnowledgeAiReviewRecordService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeDocumentVersionService;
import com.aether.knowledge.service.KnowledgeDocumentWorkflowService;
import com.aether.knowledge.service.KnowledgeReviewActionLogService;
import com.aether.knowledge.service.KnowledgeReviewTaskService;
import com.aether.knowledge.vo.KnowledgeReviewTaskDetailVo;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeReviewControllerTest {
    @BeforeEach
    void setUp() {
        new I18nUtils(mock(I18nService.class));
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, KnowledgeAiReview.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeAiReviewIssue.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeReviewActionLog.class);
    }

    @Test
    void taskDetailAggregatesDocumentAndVersion() {
        KnowledgeReviewTaskService taskService = mock(KnowledgeReviewTaskService.class);
        KnowledgeDocumentService documentService = mock(KnowledgeDocumentService.class);
        KnowledgeDocumentVersionService versionService = mock(KnowledgeDocumentVersionService.class);
        KnowledgeAiReviewRecordService aiReviewService = mock(KnowledgeAiReviewRecordService.class);
        KnowledgeReviewActionLogService actionLogService = mock(KnowledgeReviewActionLogService.class);
        KnowledgeReviewTask task = new KnowledgeReviewTask();
        task.setId("task-1");
        task.setKnowledgeBaseId("kb-1");
        task.setDocumentId("doc-1");
        task.setDocumentVersionId("version-1");
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId("doc-1");
        document.setTitle("Test document");
        KnowledgeDocumentVersion version = new KnowledgeDocumentVersion();
        version.setId("version-1");
        version.setVersionNo(3);
        version.setReviewStatus("SUBMITTED");
        when(taskService.getById("task-1")).thenReturn(task);
        when(documentService.getById("doc-1")).thenReturn(document);
        when(versionService.getById("version-1")).thenReturn(version);
        when(aiReviewService.getOne(any(), eq(false))).thenReturn(null);
        when(actionLogService.list(any())).thenReturn(Collections.emptyList());
        KnowledgeReviewTaskController controller = new KnowledgeReviewTaskController(taskService,
                mock(KnowledgeDocumentWorkflowService.class), mock(KnowledgeAccessService.class),
                documentService, versionService, aiReviewService,
                mock(KnowledgeAiReviewIssueService.class), actionLogService);

        KnowledgeReviewTaskDetailVo result = controller.detail("task-1").getData();

        assertEquals("Test document", result.getDocumentTitle());
        assertEquals(3, result.getVersionNo());
        assertEquals("SUBMITTED", result.getVersionReviewStatus());
        assertEquals("version-1", result.getVersion().getId());
    }

    @Test
    void latestReviewReturnsNullWhenVersionHasNotBeenReviewed() {
        KnowledgeDocumentVersionService versionService = mock(KnowledgeDocumentVersionService.class);
        KnowledgeDocumentService documentService = mock(KnowledgeDocumentService.class);
        KnowledgeAiReviewRecordService reviewService = mock(KnowledgeAiReviewRecordService.class);
        KnowledgeDocumentVersion version = new KnowledgeDocumentVersion();
        version.setId("version-1");
        version.setKnowledgeDocumentId("doc-1");
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId("doc-1");
        document.setKnowledgeBaseId("kb-1");
        when(versionService.getById("version-1")).thenReturn(version);
        when(documentService.getById("doc-1")).thenReturn(document);
        when(reviewService.getOne(any(), eq(false))).thenReturn(null);
        KnowledgeAiReviewController controller = new KnowledgeAiReviewController(reviewService,
                mock(KnowledgeAiReviewIssueService.class), mock(KnowledgeAccessService.class),
                versionService, documentService);

        assertNull(controller.latestByVersion("version-1").getData());
    }
}
