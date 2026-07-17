package com.aether.knowledge.service.impl;

import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeIndexJob;
import com.aether.knowledge.service.KnowledgeDocumentIndexService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeIndexJobService;
import com.aether.knowledge.service.KnowledgeDocumentVersionService;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import com.aether.knowledge.service.KnowledgeDocumentChunkService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeIndexWorker {
    private final KnowledgeIndexJobService jobService;
    private final KnowledgeDocumentService documentService;
    private final ObjectProvider<KnowledgeDocumentIndexService> indexServiceProvider;
    private final KnowledgeDocumentVersionService versionService;
    private final KnowledgeDocumentChunkService chunkService;

    public KnowledgeIndexWorker(KnowledgeIndexJobService jobService, KnowledgeDocumentService documentService,
                                ObjectProvider<KnowledgeDocumentIndexService> indexServiceProvider,
                                KnowledgeDocumentVersionService versionService,
                                KnowledgeDocumentChunkService chunkService) {
        this.jobService = jobService; this.documentService = documentService;
        this.indexServiceProvider = indexServiceProvider; this.versionService = versionService;
        this.chunkService = chunkService;
    }

    @Async("asyncPoolTaskExecutor")
    public void run(String jobId) {
        KnowledgeIndexJob job = jobService.getById(jobId);
        if (job == null || !"pending".equals(job.getStatus())) return;
        long now = System.currentTimeMillis();
        KnowledgeIndexJob running = new KnowledgeIndexJob(); running.setId(jobId); running.setStatus("running"); running.setStartedAt(now);
        jobService.updateById(running);
        KnowledgeDocumentVersion runningVersion = new KnowledgeDocumentVersion(); runningVersion.setId(job.getDocumentVersionId()); runningVersion.setIndexStatus(1); versionService.updateById(runningVersion);
        try {
            KnowledgeDocument document = documentService.getById(job.getDocumentId());
            if (document == null || Boolean.TRUE.equals(document.getDeleted())) throw new IllegalStateException("document not found");
            // 必须把当前任务版本写入分块，前端才能按版本查看分块内容。
            KnowledgeDocumentVersion targetVersion = versionService.getById(job.getDocumentVersionId());
            if (targetVersion == null || Boolean.TRUE.equals(targetVersion.getDeleted())) {
                throw new IllegalStateException("document version not found");
            }
            // 版本索引成功之前，旧的 currentVersionNo 和旧版本分块继续在线服务。
            indexServiceProvider.getObject().reindex(document, targetVersion);
            long chunkCount = chunkService.count(Wrappers.lambdaQuery(KnowledgeDocumentChunk.class)
                    .eq(KnowledgeDocumentChunk::getDocumentVersionId, targetVersion.getId())
                    .eq(KnowledgeDocumentChunk::getDeleted, false));
            KnowledgeIndexJob success = new KnowledgeIndexJob(); success.setId(jobId); success.setStatus("success"); success.setFinishedAt(System.currentTimeMillis());
            jobService.updateById(success);
            KnowledgeDocumentVersion version = new KnowledgeDocumentVersion(); version.setId(job.getDocumentVersionId()); version.setIndexStatus(2); version.setIndexedAt(System.currentTimeMillis()); version.setChunkCount((int) chunkCount); versionService.updateById(version);
            // 只有新版本全部分块和向量写入成功，才发布为当前检索版本；较早任务不能覆盖已发布的较新版本。
            KnowledgeDocument current = documentService.getById(job.getDocumentId());
            if (current == null || current.getCurrentVersionNo() == null
                    || targetVersion.getVersionNo() >= current.getCurrentVersionNo()) {
                KnowledgeDocument indexed = new KnowledgeDocument(); indexed.setId(job.getDocumentId()); indexed.setCurrentVersionNo(targetVersion.getVersionNo()); indexed.setChunkCount((int) chunkCount); indexed.setStatus(2); indexed.setIndexStatus(2); indexed.setIndexErrorMessage(null); indexed.setIndexedAt(System.currentTimeMillis()); documentService.updateById(indexed);
            }
        } catch (Exception e) {
            KnowledgeIndexJob update = new KnowledgeIndexJob(); update.setId(jobId); update.setRetryCount(job.getRetryCount() + 1);
            update.setErrorMessage(e.getMessage()); update.setFinishedAt(System.currentTimeMillis());
            update.setStatus(job.getRetryCount() + 1 >= job.getMaxRetryCount() ? "failed" : "pending");
            jobService.updateById(update);
            if ("failed".equals(update.getStatus())) {
                KnowledgeDocumentVersion version = new KnowledgeDocumentVersion(); version.setId(job.getDocumentVersionId()); version.setIndexStatus(3); version.setIndexErrorMessage(e.getMessage()); versionService.updateById(version);
                KnowledgeDocument failed = new KnowledgeDocument(); failed.setId(job.getDocumentId()); failed.setIndexStatus(3); failed.setIndexErrorMessage(e.getMessage()); documentService.updateById(failed);
            }
            if ("pending".equals(update.getStatus())) {
                run(jobId);
            }
        }
    }
}
