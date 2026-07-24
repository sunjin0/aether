package com.aether.knowledge.workflow;

import com.aether.exception.ServerException;
import com.aether.i18n.I18nService;
import com.aether.i18n.I18nUtils;
import com.aether.knowledge.entity.KnowledgeReviewActionLog;
import com.aether.knowledge.service.KnowledgeReviewActionLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeReviewAuditWriterTest {
    @Mock private KnowledgeReviewActionLogService actionLogService;
    private KnowledgeReviewAuditWriter writer;

    @BeforeEach
    void setUp() {
        new I18nUtils(mock(I18nService.class));
        writer = new KnowledgeReviewAuditWriter(actionLogService);
    }

    @Test
    void writesCurrentOperatorAndTransitionDetails() {
        when(actionLogService.save(any(KnowledgeReviewActionLog.class))).thenReturn(true);

        writer.write("admin-1", "task-1", "document-1", "version-1", "CLAIMED",
                "pending", "claimed", "reviewing");

        ArgumentCaptor<KnowledgeReviewActionLog> captor =
                ArgumentCaptor.forClass(KnowledgeReviewActionLog.class);
        verify(actionLogService).save(captor.capture());
        assertEquals("admin-1", captor.getValue().getOperatorId());
        assertEquals("CLAIMED", captor.getValue().getAction());
        assertEquals("pending", captor.getValue().getBeforeStatus());
        assertEquals("claimed", captor.getValue().getAfterStatus());
    }

    @Test
    void failsTheTransactionWhenAuditLogCannotBeSaved() {
        when(actionLogService.save(any(KnowledgeReviewActionLog.class))).thenReturn(false);

        assertThrows(ServerException.class, () -> writer.write(
                "admin-1", "task-1", "document-1", "version-1", "CLAIMED",
                "pending", "claimed", null));
    }
}
