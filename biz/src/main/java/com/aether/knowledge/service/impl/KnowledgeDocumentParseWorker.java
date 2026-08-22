package com.aether.knowledge.service.impl;

import com.aether.i18n.I18nUtils;
import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.knowledge.service.KnowledgeBaseService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeDocumentWorkflowService;
import com.aether.local.CurrentUser;
import com.aether.storage.service.ObjectStorageService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts uploaded knowledge documents after their upload transaction commits.
 */
@Component
public class KnowledgeDocumentParseWorker {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentParseWorker.class);

    private final KnowledgeDocumentService documentService;
    private final KnowledgeDocumentContentExtractor contentExtractor;
    private final KnowledgeDocumentWorkflowService workflowService;
    private final ObjectStorageService objectStorageService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeDocumentMarkdownFormatter markdownFormatter;
    private final TaskExecutor taskExecutor;
    private final Set<String> runningDocumentIds = ConcurrentHashMap.newKeySet();

    public KnowledgeDocumentParseWorker(KnowledgeDocumentService documentService,
                                        KnowledgeDocumentContentExtractor contentExtractor,
                                        KnowledgeDocumentWorkflowService workflowService,
                                        ObjectStorageService objectStorageService,
                                        KnowledgeBaseService knowledgeBaseService,
                                        KnowledgeDocumentMarkdownFormatter markdownFormatter,
                                        @Qualifier("asyncPoolTaskExecutor") TaskExecutor taskExecutor) {
        this.documentService = documentService;
        this.contentExtractor = contentExtractor;
        this.workflowService = workflowService;
        this.objectStorageService = objectStorageService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.markdownFormatter = markdownFormatter;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Submits AnyDoc conversion and draft creation outside the HTTP request.
     */
    public void submit(String documentId, String operatorId) {
        if (StringUtils.isBlank(documentId) || !runningDocumentIds.add(documentId)) return;
        try {
            taskExecutor.execute(() -> run(documentId, operatorId));
        } catch (RuntimeException e) {
            runningDocumentIds.remove(documentId);
            throw e;
        }
    }

    /**
     * Recovers uploads left in processing status by a restart or failed dispatch.
     */
    @Scheduled(initialDelay = 5000L, fixedDelay = 30000L)
    public void resumePendingDocuments() {
        List<KnowledgeDocument> pending = documentService.list(Wrappers.lambdaQuery(KnowledgeDocument.class)
                .eq(KnowledgeDocument::getStatus, 1)
                .eq(KnowledgeDocument::getDeleted, false)
                .orderByAsc(KnowledgeDocument::getCreatedAt));
        for (KnowledgeDocument document : pending) {
            submit(document.getId(), null);
        }
    }

    /**
     * Performs parsing in the application executor.
     */
    private void run(String documentId, String operatorId) {
        try {
            log.info("Knowledge document parsing started: documentId={}", documentId);
            KnowledgeDocument document = documentService.getById(documentId);
            if (document == null || Boolean.TRUE.equals(document.getDeleted()) || document.getStatus() == null
                    || document.getStatus() != 1) {
                return;
            }
            if (StringUtils.isBlank(document.getStorageBucket()) || StringUtils.isBlank(document.getStorageObjectKey())) {
                throw new IllegalStateException("knowledge source object is missing");
            }
            byte[] source = objectStorageService.getObject(document.getStorageBucket(), document.getStorageObjectKey());
            String extractedText = contentExtractor.extract(document.getOriginalFileName(), source);
            KnowledgeBase base = knowledgeBaseService.getById(document.getKnowledgeBaseId());
            String markdown = markdownFormatter.formatIfConfigured(base, document.getTitle(), extractedText);
            document.setContent(markdown);
            bindOperator(operatorId, base);
            workflowService.createDraft(document, null);
            documentService.update(Wrappers.lambdaUpdate(KnowledgeDocument.class)
                    .eq(KnowledgeDocument::getId, documentId)
                    .eq(KnowledgeDocument::getStatus, 1)
                    .set(KnowledgeDocument::getStatus, 2)
                    .set(KnowledgeDocument::getIndexErrorMessage, null));
            log.info("Knowledge document parsing completed: documentId={}", documentId);
        } catch (Exception e) {
            log.error("Knowledge document parsing failed: documentId={}", documentId, e);
            documentService.update(Wrappers.lambdaUpdate(KnowledgeDocument.class)
                    .eq(KnowledgeDocument::getId, documentId)
                    .eq(KnowledgeDocument::getStatus, 1)
                    .set(KnowledgeDocument::getStatus, 0)
                    .set(KnowledgeDocument::getIndexStatus, 3)
                    .set(KnowledgeDocument::getIndexErrorMessage, I18nUtils.getMessage("knowledge.document.parse.failed")));
        } finally {
            CurrentUser.remove();
            runningDocumentIds.remove(documentId);
        }
    }

    /**
     * Binds the original uploader for audit writing. Recovery tasks can run after
     * a restart, so they fall back to the knowledge-base owner when that request
     * context is no longer available.
     */
    private void bindOperator(String operatorId, KnowledgeBase base) {
        String actorId = StringUtils.defaultIfBlank(operatorId, base == null ? null : base.getOwnerAdminId());
        if (StringUtils.isBlank(actorId)) {
            throw new IllegalStateException("knowledge document operator is missing");
        }
        HashMap<String, String> user = new HashMap<>();
        user.put("userId", actorId);
        CurrentUser.set(user);
    }
}
