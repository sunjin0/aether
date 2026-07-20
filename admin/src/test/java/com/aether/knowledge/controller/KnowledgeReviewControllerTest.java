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
import com.aether.knowledge.vo.KnowledgeAiReviewDiffVo;
import com.aether.knowledge.vo.KnowledgeAiReviewIssueAcceptVo;
import com.aether.knowledge.vo.KnowledgeAiReviewIssueBatchAcceptVo;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

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
                versionService, documentService, mock(KnowledgeDocumentWorkflowService.class));

        assertNull(controller.latestByVersion("version-1").getData());
    }

    @Test
    void diffAggregatesIssuesAndLineLocations() {
        KnowledgeAiReviewRecordService reviewService = mock(KnowledgeAiReviewRecordService.class);
        KnowledgeAiReviewIssueService issueService = mock(KnowledgeAiReviewIssueService.class);
        KnowledgeDocumentVersionService versionService = mock(KnowledgeDocumentVersionService.class);
        KnowledgeAiReview review = new KnowledgeAiReview();
        review.setId("review-1");
        review.setKnowledgeBaseId("kb-1");
        review.setDocumentId("doc-1");
        review.setDocumentVersionId("version-1");
        review.setSourceChecksum("checksum-1");
        review.setSourceContent("title\nold text");
        KnowledgeDocumentVersion version = new KnowledgeDocumentVersion();
        version.setId("version-1");
        version.setOriginalContent("title\nold text");
        version.setContent("title\nold text");
        version.setContentChecksum("checksum-1");
        version.setReviewStatus("AI_REVIEWED");
        KnowledgeAiReviewIssue issue = new KnowledgeAiReviewIssue();
        issue.setId("issue-1");
        issue.setSeverity("critical");
        issue.setOriginalExcerpt("old text");
        issue.setSuggestedPatch("{\"operation\":\"replace\",\"replacement\":\"new text\"}");
        issue.setHandleStatus("pending");
        when(reviewService.getById("review-1")).thenReturn(review);
        when(versionService.getById("version-1")).thenReturn(version);
        when(issueService.list(any())).thenReturn(Collections.singletonList(issue));
        KnowledgeAiReviewController controller = new KnowledgeAiReviewController(reviewService, issueService,
                mock(KnowledgeAccessService.class), versionService, mock(KnowledgeDocumentService.class),
                mock(KnowledgeDocumentWorkflowService.class));

        KnowledgeAiReviewDiffVo result = controller.diff("review-1").getData();

        assertEquals(1, result.getPendingCount());
        assertEquals(1, result.getCriticalPendingCount());
        assertEquals(2, result.getIssues().get(0).getBaseStartLine());
        assertEquals("replace", result.getIssues().get(0).getSuggestedPatch().getString("operation"));
        assertEquals("title\nnew text", result.getProposedContent());
    }

    @Test
    void acceptingSuggestionStagesPatchWithoutUpdatingDraft() {
        KnowledgeAiReviewRecordService reviewService = mock(KnowledgeAiReviewRecordService.class);
        KnowledgeAiReviewIssueService issueService = mock(KnowledgeAiReviewIssueService.class);
        KnowledgeDocumentVersionService versionService = mock(KnowledgeDocumentVersionService.class);
        KnowledgeDocumentWorkflowService workflowService = mock(KnowledgeDocumentWorkflowService.class);
        KnowledgeAiReview review = new KnowledgeAiReview();
        review.setId("review-1");
        review.setKnowledgeBaseId("kb-1");
        review.setSourceChecksum("checksum-1");
        review.setStatus("success");
        KnowledgeAiReviewIssue issue = new KnowledgeAiReviewIssue();
        issue.setId("issue-1");
        issue.setAiReviewId("review-1");
        issue.setDocumentVersionId("version-1");
        issue.setOriginalExcerpt("old text");
        issue.setSuggestedPatch("{\"operation\":\"replace\",\"replacement\":\"new text\"}");
        issue.setHandleStatus("pending");
        KnowledgeDocumentVersion version = new KnowledgeDocumentVersion();
        version.setId("version-1");
        version.setContent("old text");
        version.setContentChecksum("checksum-1");
        version.setReviewStatus("AI_REVIEWED");
        when(reviewService.getById("review-1")).thenReturn(review);
        when(issueService.getById("issue-1")).thenReturn(issue);
        when(versionService.getById("version-1")).thenReturn(version);
        when(issueService.update(any())).thenReturn(true);
        KnowledgeAiReviewController controller = new KnowledgeAiReviewController(reviewService, issueService,
                mock(KnowledgeAccessService.class), versionService, mock(KnowledgeDocumentService.class), workflowService);
        KnowledgeAiReviewIssueAcceptVo request = new KnowledgeAiReviewIssueAcceptVo();
        request.setExpectedChecksum("checksum-1");

        assertEquals("checksum-1", controller.acceptIssue("review-1", "issue-1", request).getData().getContentChecksum());
        verify(workflowService, never()).updateDraft(any(), any(), any());
    }

    @Test
    void applyAcceptedIssuesUpdatesDraftOnce() {
        KnowledgeAiReviewRecordService reviewService = mock(KnowledgeAiReviewRecordService.class);
        KnowledgeAiReviewIssueService issueService = mock(KnowledgeAiReviewIssueService.class);
        KnowledgeDocumentVersionService versionService = mock(KnowledgeDocumentVersionService.class);
        KnowledgeDocumentWorkflowService workflowService = mock(KnowledgeDocumentWorkflowService.class);
        KnowledgeAiReview review = new KnowledgeAiReview();
        review.setId("review-1"); review.setKnowledgeBaseId("kb-1"); review.setDocumentVersionId("version-1");
        review.setSourceChecksum("checksum-1"); review.setStatus("success");
        KnowledgeAiReviewIssue issue = issue("issue-1", "review-1", "version-1", "old text", "new text");
        issue.setHandleStatus("accepted"); issue.setAppliedContent("new text");
        KnowledgeDocumentVersion version = new KnowledgeDocumentVersion();
        version.setId("version-1"); version.setContent("old text");
        version.setContentChecksum("checksum-1"); version.setReviewStatus("AI_REVIEWED");
        KnowledgeDocumentVersion updatedVersion = new KnowledgeDocumentVersion();
        updatedVersion.setId("version-1"); updatedVersion.setContentChecksum("checksum-2"); updatedVersion.setReviewStatus("DRAFT");
        when(reviewService.getById("review-1")).thenReturn(review);
        when(versionService.getById("version-1")).thenReturn(version);
        when(issueService.list(any())).thenReturn(Collections.singletonList(issue));
        when(workflowService.updateDraft("version-1", "new text", "checksum-1")).thenReturn(updatedVersion);
        KnowledgeAiReviewController controller = new KnowledgeAiReviewController(reviewService, issueService,
                mock(KnowledgeAccessService.class), versionService, mock(KnowledgeDocumentService.class), workflowService);

        KnowledgeAiReviewIssueAcceptVo request = new KnowledgeAiReviewIssueAcceptVo();
        request.setExpectedChecksum("checksum-1");

        assertEquals("checksum-2", controller.applyAcceptedIssues("review-1", request).getData().getContentChecksum());
        verify(workflowService).updateDraft("version-1", "new text", "checksum-1");
    }

    @Test
    void batchAcceptStagesNonCriticalPatchesWithoutUpdatingDraft() {
        KnowledgeAiReviewRecordService reviewService = mock(KnowledgeAiReviewRecordService.class);
        KnowledgeAiReviewIssueService issueService = mock(KnowledgeAiReviewIssueService.class);
        KnowledgeDocumentVersionService versionService = mock(KnowledgeDocumentVersionService.class);
        KnowledgeDocumentWorkflowService workflowService = mock(KnowledgeDocumentWorkflowService.class);
        KnowledgeAiReview review = new KnowledgeAiReview();
        review.setId("review-1"); review.setKnowledgeBaseId("kb-1"); review.setDocumentVersionId("version-1");
        review.setSourceChecksum("checksum-1"); review.setStatus("success");
        KnowledgeAiReviewIssue first = issue("issue-1", "review-1", "version-1", "old first", "new first");
        KnowledgeAiReviewIssue second = issue("issue-2", "review-1", "version-1", "old second", "new second");
        KnowledgeDocumentVersion version = new KnowledgeDocumentVersion();
        version.setId("version-1"); version.setContent("old first\nold second");
        version.setContentChecksum("checksum-1"); version.setReviewStatus("AI_REVIEWED");
        when(reviewService.getById("review-1")).thenReturn(review);
        when(issueService.getById("issue-1")).thenReturn(first);
        when(issueService.getById("issue-2")).thenReturn(second);
        when(versionService.getById("version-1")).thenReturn(version);
        when(issueService.update(any())).thenReturn(true);
        KnowledgeAiReviewController controller = new KnowledgeAiReviewController(reviewService, issueService,
                mock(KnowledgeAccessService.class), versionService, mock(KnowledgeDocumentService.class), workflowService);
        KnowledgeAiReviewIssueBatchAcceptVo request = new KnowledgeAiReviewIssueBatchAcceptVo();
        request.setIssueIds(Arrays.asList("issue-1", "issue-2")); request.setExpectedChecksum("checksum-1");

        assertEquals("checksum-1", controller.acceptIssues("review-1", request).getData().getContentChecksum());
        verify(workflowService, never()).updateDraft(any(), any(), any());
    }

    @Test
    void rejectDoesNotAllowSubmittedDocumentVersion() {
        KnowledgeAiReviewRecordService reviewService = mock(KnowledgeAiReviewRecordService.class);
        KnowledgeAiReviewIssueService issueService = mock(KnowledgeAiReviewIssueService.class);
        KnowledgeDocumentVersionService versionService = mock(KnowledgeDocumentVersionService.class);
        KnowledgeAiReview review = new KnowledgeAiReview();
        review.setId("review-1"); review.setKnowledgeBaseId("kb-1");
        KnowledgeAiReviewIssue issue = issue("issue-1", "review-1", "version-1", "old", "new");
        KnowledgeDocumentVersion version = new KnowledgeDocumentVersion();
        version.setId("version-1"); version.setReviewStatus("SUBMITTED");
        when(reviewService.getById("review-1")).thenReturn(review);
        when(issueService.getById("issue-1")).thenReturn(issue);
        when(versionService.getById("version-1")).thenReturn(version);
        KnowledgeAiReviewController controller = new KnowledgeAiReviewController(reviewService, issueService,
                mock(KnowledgeAccessService.class), versionService, mock(KnowledgeDocumentService.class),
                mock(KnowledgeDocumentWorkflowService.class));

        assertThrows(com.aether.exception.ServerException.class,
                () -> controller.rejectIssue("review-1", "issue-1", null));
    }

    private KnowledgeAiReviewIssue issue(String id, String reviewId, String versionId, String original, String replacement) {
        KnowledgeAiReviewIssue issue = new KnowledgeAiReviewIssue();
        issue.setId(id); issue.setAiReviewId(reviewId); issue.setDocumentVersionId(versionId);
        issue.setSeverity("warning"); issue.setOriginalExcerpt(original); issue.setHandleStatus("pending");
        issue.setSuggestedPatch("{\"operation\":\"replace\",\"replacement\":\"" + replacement + "\"}");
        return issue;
    }
}
