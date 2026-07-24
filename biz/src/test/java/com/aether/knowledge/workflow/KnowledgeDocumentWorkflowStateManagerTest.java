package com.aether.knowledge.workflow;

import com.aether.exception.ServerException;
import com.aether.i18n.I18nService;
import com.aether.i18n.I18nUtils;
import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.entity.KnowledgeReviewTask;
import com.aether.knowledge.model.KnowledgeReviewStatus;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeDocumentVersionService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentWorkflowStateManagerTest {
    @Mock private KnowledgeDocumentService documentService;
    @Mock private KnowledgeDocumentVersionService versionService;

    private KnowledgeDocumentWorkflowStateManager stateManager;

    @BeforeEach
    void setUp() {
        new I18nUtils(mock(I18nService.class));
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "state-manager-test");
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocument.class);
        stateManager = new KnowledgeDocumentWorkflowStateManager(documentService, versionService);
    }

    @Test
    void returnsActiveSubmissionWhenDocumentVersionAndChecksumMatch() {
        KnowledgeDocument document = submittedDocument();
        KnowledgeDocumentVersion version = submittedVersion();
        KnowledgeReviewTask task = task();
        when(documentService.getById("document-1")).thenReturn(document);
        when(versionService.getById("version-1")).thenReturn(version);

        KnowledgeDocumentWorkflowStateManager.ActiveSubmission result =
                stateManager.requireActiveSubmission(task);

        assertEquals(document, result.getDocument());
        assertEquals(version, result.getVersion());
    }

    @Test
    void rejectsSubmissionWhenChecksumNoLongerMatches() {
        KnowledgeDocumentVersion version = submittedVersion();
        version.setContentChecksum("changed");
        when(documentService.getById("document-1")).thenReturn(submittedDocument());
        when(versionService.getById("version-1")).thenReturn(version);

        assertThrows(ServerException.class,
                () -> stateManager.requireActiveSubmission(task()));
    }

    @Test
    void marksSubmissionUsingBothDocumentPointersAsCompareAndSetConditions() {
        when(documentService.update(any())).thenReturn(true);

        stateManager.markSubmitted("document-1", "version-1", 100L);

        ArgumentCaptor<LambdaUpdateWrapper> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(documentService).update(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("draft_version_id"));
        assertTrue(sql.contains("submitted_version_id"));
    }

    @Test
    void reportsConflictWhenDocumentCompareAndSetFails() {
        when(documentService.update(any())).thenReturn(false);

        assertThrows(ServerException.class,
                () -> stateManager.finishSubmission("document-1", "version-1",
                        KnowledgeReviewStatus.APPROVED, 100L, 1));
    }

    private KnowledgeDocument submittedDocument() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId("document-1");
        document.setKnowledgeBaseId("kb-1");
        document.setSubmittedVersionId("version-1");
        document.setReviewStatus(KnowledgeReviewStatus.SUBMITTED);
        return document;
    }

    private KnowledgeDocumentVersion submittedVersion() {
        KnowledgeDocumentVersion version = new KnowledgeDocumentVersion();
        version.setId("version-1");
        version.setKnowledgeDocumentId("document-1");
        version.setReviewStatus(KnowledgeReviewStatus.SUBMITTED);
        version.setContentChecksum("checksum");
        return version;
    }

    private KnowledgeReviewTask task() {
        KnowledgeReviewTask task = new KnowledgeReviewTask();
        task.setKnowledgeBaseId("kb-1");
        task.setDocumentId("document-1");
        task.setDocumentVersionId("version-1");
        task.setSourceChecksum("checksum");
        return task;
    }
}
