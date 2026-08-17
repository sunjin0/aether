package com.aether.knowledge.service.impl;

import com.aether.exception.ServerException;
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
import com.aether.knowledge.service.KnowledgeReviewActionLogService;
import com.aether.knowledge.service.KnowledgeReviewTaskService;
import com.aether.knowledge.vo.KnowledgeReviewTaskDetailVo;
import com.aether.knowledge.vo.KnowledgeReviewTaskQueryVo;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证知识库审核任务查询服务实现的行为。
 */
class KnowledgeReviewTaskQueryServiceImplTest {
    private KnowledgeReviewTaskService taskService;
    private KnowledgeAccessService accessService;
    private KnowledgeDocumentService documentService;
    private KnowledgeDocumentVersionService versionService;
    private KnowledgeAiReviewRecordService aiReviewService;
    private KnowledgeAiReviewIssueService aiIssueService;
    private KnowledgeReviewActionLogService actionLogService;
    private KnowledgeReviewTaskQueryServiceImpl queryService;

    /**
     * 处理setUp。
     */
    @BeforeEach
    void setUp() {
        new I18nUtils(mock(I18nService.class));
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, KnowledgeAiReview.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeAiReviewIssue.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeReviewActionLog.class);
        taskService = mock(KnowledgeReviewTaskService.class);
        accessService = mock(KnowledgeAccessService.class);
        documentService = mock(KnowledgeDocumentService.class);
        versionService = mock(KnowledgeDocumentVersionService.class);
        aiReviewService = mock(KnowledgeAiReviewRecordService.class);
        aiIssueService = mock(KnowledgeAiReviewIssueService.class);
        actionLogService = mock(KnowledgeReviewActionLogService.class);
        queryService = new KnowledgeReviewTaskQueryServiceImpl(taskService, accessService,
                documentService, versionService, aiReviewService, aiIssueService, actionLogService);
    }

    /**
     * 查询RejectsUnknown状态BeforeLoadingAccessible知识库Bases。
     */
    @Test
    void listRejectsUnknownStatusBeforeLoadingAccessibleKnowledgeBases() {
        KnowledgeReviewTaskQueryVo query = new KnowledgeReviewTaskQueryVo();
        query.setStatus("unknown");

        assertThrows(ServerException.class, () -> queryService.list(query));

        verify(accessService, never()).readableKnowledgeBaseIds();
    }

    /**
     * 查询ReturnsEmpty分页查询WithoutExecutingSqlWhenNothing判断是否为Readable。
     */
    @Test
    void listReturnsEmptyPageWithoutExecutingSqlWhenNothingIsReadable() {
        when(accessService.readableKnowledgeBaseIds()).thenReturn(Collections.emptyList());

        com.baomidou.mybatisplus.core.metadata.IPage<?> result =
                queryService.list(new KnowledgeReviewTaskQueryVo());

        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
        verifyNoInteractions(taskService);
    }

    /**
     * 详情Aggregates文档VersionAnd审核历史记录。
     */
    @Test
    void detailAggregatesDocumentVersionAndReviewHistory() {
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
        KnowledgeAiReview review = new KnowledgeAiReview();
        review.setId("review-1");
        KnowledgeAiReviewIssue issue = new KnowledgeAiReviewIssue();
        issue.setId("issue-1");
        KnowledgeReviewActionLog log = new KnowledgeReviewActionLog();
        log.setId("log-1");
        when(taskService.getById("task-1")).thenReturn(task);
        when(documentService.getById("doc-1")).thenReturn(document);
        when(versionService.getById("version-1")).thenReturn(version);
        when(aiReviewService.getOne(any(), eq(false))).thenReturn(review);
        when(aiIssueService.list(any())).thenReturn(Collections.singletonList(issue));
        when(actionLogService.list(any())).thenReturn(Collections.singletonList(log));

        KnowledgeReviewTaskDetailVo result = queryService.detail("task-1");

        assertEquals("Test document", result.getDocumentTitle());
        assertEquals(3, result.getVersionNo());
        assertEquals("SUBMITTED", result.getVersionReviewStatus());
        assertEquals("review-1", result.getAiReview().getId());
        assertEquals("issue-1", result.getIssues().get(0).getId());
        assertEquals("log-1", result.getActionLogs().get(0).getId());
        verify(accessService).requireReadable("kb-1");
    }

    /**
     * 详情RejectsDeleted任务BeforeAccess检查。
     */
    @Test
    void detailRejectsDeletedTaskBeforeAccessCheck() {
        KnowledgeReviewTask task = new KnowledgeReviewTask();
        task.setDeleted(true);
        when(taskService.getById("task-1")).thenReturn(task);

        assertThrows(ServerException.class, () -> queryService.detail("task-1"));

        verify(accessService, never()).requireReadable(any());
    }
}
