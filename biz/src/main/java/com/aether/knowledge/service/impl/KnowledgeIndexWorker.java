package com.aether.knowledge.service.impl;

import com.aether.i18n.I18nUtils;
import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeIndexJob;
import com.aether.knowledge.service.KnowledgeDocumentIndexService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeIndexJobService;
import com.aether.knowledge.service.KnowledgeDocumentVersionService;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import com.aether.knowledge.service.KnowledgeDocumentChunkService;
import com.aether.knowledge.model.KnowledgeJobType;
import com.aether.knowledge.model.KnowledgeReviewStatus;
import com.aether.knowledge.model.KnowledgeIndexJobStatus;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeIndexWorker {
    private static final long RUNNING_LEASE_MILLIS = 30L * 60L * 1000L;
    private final KnowledgeIndexJobService jobService;
    private final KnowledgeDocumentService documentService;
    private final ObjectProvider<KnowledgeDocumentIndexService> indexServiceProvider;
    private final KnowledgeDocumentVersionService versionService;
    private final KnowledgeDocumentChunkService chunkService;
    private final ObjectProvider<KnowledgeIndexWorker> selfProvider;
    private final TaskScheduler taskScheduler;

    public KnowledgeIndexWorker(KnowledgeIndexJobService jobService, KnowledgeDocumentService documentService,
                                ObjectProvider<KnowledgeDocumentIndexService> indexServiceProvider,
                                KnowledgeDocumentVersionService versionService,
                                KnowledgeDocumentChunkService chunkService,
                                ObjectProvider<KnowledgeIndexWorker> selfProvider,
                                TaskScheduler taskScheduler) {
        this.jobService = jobService; this.documentService = documentService;
        this.indexServiceProvider = indexServiceProvider; this.versionService = versionService;
        this.chunkService = chunkService;
        this.selfProvider = selfProvider;
        this.taskScheduler = taskScheduler;
    }

    @Async("asyncPoolTaskExecutor")
    public void run(String jobId) {
        long now = System.currentTimeMillis();
        // Only one thread or application instance may transition a pending job to running.
        if (!jobService.claimPending(jobId, now)) return;
        KnowledgeIndexJob job = jobService.getById(jobId);
        if (job == null) return;
        KnowledgeDocumentVersion runningVersion = new KnowledgeDocumentVersion(); runningVersion.setId(job.getDocumentVersionId()); runningVersion.setIndexStatus(1); versionService.updateById(runningVersion);
        try {
            KnowledgeDocument document = documentService.getById(job.getDocumentId());
            if (document == null || Boolean.TRUE.equals(document.getDeleted())) throw new IllegalStateException("document not found");
            // 必须把当前任务版本写入分块，前端才能按版本查看分块内容。
            KnowledgeDocumentVersion targetVersion = versionService.getById(job.getDocumentVersionId());
            if (targetVersion == null || Boolean.TRUE.equals(targetVersion.getDeleted())) {
                throw new IllegalStateException("document version not found");
            }
            if (!KnowledgeJobType.REINDEX.equals(job.getJobType())
                    && !KnowledgeReviewStatus.APPROVED.equals(targetVersion.getReviewStatus())) {
                throw new IllegalStateException("document version is not approved");
            }
            // 版本索引成功之前，旧的 currentVersionNo 和旧版本分块继续在线服务。
            indexServiceProvider.getObject().reindex(document, targetVersion);
            long chunkCount = chunkService.count(Wrappers.lambdaQuery(KnowledgeDocumentChunk.class)
                    .eq(KnowledgeDocumentChunk::getDocumentVersionId, targetVersion.getId())
                    .eq(KnowledgeDocumentChunk::getDeleted, false));
            boolean completed = jobService.update(Wrappers.lambdaUpdate(KnowledgeIndexJob.class)
                    .eq(KnowledgeIndexJob::getId, jobId)
                    .eq(KnowledgeIndexJob::getStatus, KnowledgeIndexJobStatus.RUNNING)
                    .eq(KnowledgeIndexJob::getStartedAt, job.getStartedAt())
                    .set(KnowledgeIndexJob::getStatus, KnowledgeIndexJobStatus.SUCCESS)
                    .set(KnowledgeIndexJob::getFinishedAt, System.currentTimeMillis()));
            // A recovered lease owns publication; an expired worker may not publish stale state.
            if (!completed) return;
            KnowledgeDocumentVersion version = new KnowledgeDocumentVersion(); version.setId(job.getDocumentVersionId()); version.setIndexStatus(2); version.setIndexedAt(System.currentTimeMillis()); version.setChunkCount((int) chunkCount); versionService.updateById(version);
            // 只有新版本全部分块和向量写入成功，才发布为当前检索版本；较早任务不能覆盖已发布的较新版本。
            KnowledgeDocument current = documentService.getById(job.getDocumentId());
            if (current == null || current.getCurrentVersionNo() == null
                    || targetVersion.getVersionNo() >= current.getCurrentVersionNo()) {
                KnowledgeDocument indexed = new KnowledgeDocument(); indexed.setId(job.getDocumentId()); indexed.setContent(targetVersion.getContent()); indexed.setCurrentVersionNo(targetVersion.getVersionNo()); indexed.setChunkCount((int) chunkCount); indexed.setStatus(2); indexed.setIndexStatus(2); indexed.setIndexErrorMessage(null); indexed.setIndexedAt(System.currentTimeMillis()); documentService.updateById(indexed);
            }
        } catch (Exception e) {
            int retryCount = job.getRetryCount() + 1;
            String nextStatus = retryCount >= job.getMaxRetryCount()
                    ? KnowledgeIndexJobStatus.FAILED : KnowledgeIndexJobStatus.PENDING;
            boolean released = jobService.update(Wrappers.lambdaUpdate(KnowledgeIndexJob.class)
                    .eq(KnowledgeIndexJob::getId, jobId)
                    .eq(KnowledgeIndexJob::getStatus, KnowledgeIndexJobStatus.RUNNING)
                    .eq(KnowledgeIndexJob::getStartedAt, job.getStartedAt())
                    .set(KnowledgeIndexJob::getRetryCount, retryCount)
                    .set(KnowledgeIndexJob::getErrorMessage, I18nUtils.getMessage("knowledge.index.failed"))
                    .set(KnowledgeIndexJob::getFinishedAt, System.currentTimeMillis())
                    .set(KnowledgeIndexJob::getStatus, nextStatus));
            if (!released) return;
            if (KnowledgeIndexJobStatus.FAILED.equals(nextStatus)) {
                KnowledgeDocumentVersion version = new KnowledgeDocumentVersion(); version.setId(job.getDocumentVersionId()); version.setIndexStatus(3); version.setIndexErrorMessage(I18nUtils.getMessage("knowledge.index.failed")); versionService.updateById(version);
                KnowledgeDocument current = documentService.getById(job.getDocumentId());
                KnowledgeDocumentVersion failedVersion = versionService.getById(job.getDocumentVersionId());
                if (current != null && failedVersion != null && (current.getCurrentVersionNo() == null
                        || failedVersion.getVersionNo() > current.getCurrentVersionNo())) {
                    KnowledgeDocument failed = new KnowledgeDocument(); failed.setId(job.getDocumentId()); failed.setIndexStatus(3); failed.setIndexErrorMessage(I18nUtils.getMessage("knowledge.index.failed")); documentService.updateById(failed);
                }
            }
            if (KnowledgeIndexJobStatus.PENDING.equals(nextStatus)) {
                long delayMillis = Math.min(30000L, 1000L << Math.min(5, retryCount - 1));
                taskScheduler.schedule(() -> selfProvider.getObject().run(jobId),
                        new java.util.Date(System.currentTimeMillis() + delayMillis));
            }
        }
    }

    /** Recover jobs left pending by a restart or a previously saturated executor. */
    @Scheduled(fixedDelay = 30000L, initialDelay = 30000L)
    public void dispatchPendingJobs() {
        long staleBefore = System.currentTimeMillis() - RUNNING_LEASE_MILLIS;
        jobService.update(Wrappers.lambdaUpdate(KnowledgeIndexJob.class)
                .eq(KnowledgeIndexJob::getStatus, KnowledgeIndexJobStatus.RUNNING)
                .lt(KnowledgeIndexJob::getStartedAt, staleBefore)
                .eq(KnowledgeIndexJob::getDeleted, false)
                .set(KnowledgeIndexJob::getStatus, KnowledgeIndexJobStatus.PENDING)
                .set(KnowledgeIndexJob::getErrorMessage, I18nUtils.getMessage("knowledge.index.lease.expired")));
        jobService.list(Wrappers.lambdaQuery(KnowledgeIndexJob.class)
                        .eq(KnowledgeIndexJob::getStatus, KnowledgeIndexJobStatus.PENDING)
                        .eq(KnowledgeIndexJob::getDeleted, false)
                        .orderByAsc(KnowledgeIndexJob::getCreatedAt)
                        .last("LIMIT 20"))
                .forEach(job -> selfProvider.getObject().run(job.getId()));
    }
}
