package com.aether.knowledge.service.impl;

import com.aether.i18n.I18nUtils;
import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeDocumentWorkflowService;
import com.aether.local.CurrentUser;
import com.aether.storage.service.ObjectStorageService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;

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

    public KnowledgeDocumentParseWorker(KnowledgeDocumentService documentService,
                                        KnowledgeDocumentContentExtractor contentExtractor,
                                        KnowledgeDocumentWorkflowService workflowService,
                                        ObjectStorageService objectStorageService) {
        this.documentService = documentService;
        this.contentExtractor = contentExtractor;
        this.workflowService = workflowService;
        this.objectStorageService = objectStorageService;
    }

    /**
     * Runs AnyDoc conversion and draft creation outside the HTTP request.
     */
    @Async("asyncPoolTaskExecutor")
    public void run(String documentId, String operatorId) {
        HashMap<String, String> user = new HashMap<>();
        user.put("userId", operatorId);
        CurrentUser.set(user);
        try {
            KnowledgeDocument document = documentService.getById(documentId);
            if (document == null || Boolean.TRUE.equals(document.getDeleted()) || document.getStatus() == null
                    || document.getStatus() != 1) {
                return;
            }
            if (StringUtils.isBlank(document.getStorageBucket()) || StringUtils.isBlank(document.getStorageObjectKey())) {
                throw new IllegalStateException("knowledge source object is missing");
            }
            byte[] source = objectStorageService.getObject(document.getStorageBucket(), document.getStorageObjectKey());
            String markdown = contentExtractor.extract(document.getOriginalFileName(), source);
            document.setContent(markdown);
            workflowService.createDraft(document, null);
            documentService.update(Wrappers.lambdaUpdate(KnowledgeDocument.class)
                    .eq(KnowledgeDocument::getId, documentId)
                    .eq(KnowledgeDocument::getStatus, 1)
                    .set(KnowledgeDocument::getStatus, 2)
                    .set(KnowledgeDocument::getIndexErrorMessage, null));
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
        }
    }
}
