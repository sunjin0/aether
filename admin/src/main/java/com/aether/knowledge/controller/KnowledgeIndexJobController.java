package com.aether.knowledge.controller;

import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.entity.KnowledgeIndexJob;
import com.aether.knowledge.service.KnowledgeDocumentIndexService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.model.KnowledgeJobType;
import com.aether.knowledge.service.KnowledgeDocumentVersionService;
import com.aether.knowledge.service.KnowledgeIndexJobService;
import com.aether.knowledge.service.KnowledgeAccessService;
import com.aether.knowledge.vo.KnowledgeIndexJobQueryVo;
import com.aether.permission.Permission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * 提供知识库索引Job相关的 REST 接口。
 */
@RestController
@RequestMapping("/api/knowledge/index-job")
@Permission(path = "/knowledge/document")
public class KnowledgeIndexJobController {
    private final KnowledgeIndexJobService jobService;
    private final KnowledgeDocumentService documentService;
    private final KnowledgeDocumentVersionService versionService;
    private final KnowledgeDocumentIndexService indexService;
    private final KnowledgeAccessService accessService;

    /**
     * 创建 {@code KnowledgeIndexJobController} 实例。
     */
    public KnowledgeIndexJobController(KnowledgeIndexJobService jobService, KnowledgeDocumentService documentService,
                                       KnowledgeDocumentVersionService versionService, KnowledgeDocumentIndexService indexService,
                                       KnowledgeAccessService accessService) {
        this.jobService = jobService;
        this.documentService = documentService;
        this.versionService = versionService;
        this.indexService = indexService;
        this.accessService = accessService;
    }

    /**
     * 查询当前请求。
     */
    @PostMapping("/list")
    public WebResponse<List<KnowledgeIndexJob>> list(@RequestBody(required = false) KnowledgeIndexJobQueryVo query) {
        if (query == null) query = new KnowledgeIndexJobQueryVo();
        List<String> readableIds = accessService.readableKnowledgeBaseIds();
        long current = query.getCurrent() == null || query.getCurrent() < 1 ? 1 : query.getCurrent();
        long pageSize = query.getPageSize() == null ? 20 : Math.max(1, Math.min(100, query.getPageSize()));
        if (readableIds.isEmpty()) {
            return WebResponse.Page(Collections.emptyList(), 0L);
        }
        Page<KnowledgeIndexJob> page = jobService.page(new Page<>(current, pageSize), Wrappers.lambdaQuery(KnowledgeIndexJob.class)
                .in(KnowledgeIndexJob::getKnowledgeBaseId, readableIds)
                .eq(query.getJobType() != null, KnowledgeIndexJob::getJobType, query.getJobType())
                .eq(query.getKnowledgeBaseId() != null, KnowledgeIndexJob::getKnowledgeBaseId, query.getKnowledgeBaseId())
                .eq(query.getDocumentId() != null, KnowledgeIndexJob::getDocumentId, query.getDocumentId())
                .eq(query.getStatus() != null, KnowledgeIndexJob::getStatus, query.getStatus()).eq(KnowledgeIndexJob::getDeleted, false).orderByDesc(KnowledgeIndexJob::getCreatedAt));
        return WebResponse.Page(page.getRecords(), page.getTotal());
    }

    /**
     * 详情当前请求。
     */
    @GetMapping("/{id}")
    public WebResponse<KnowledgeIndexJob> detail(@PathVariable String id) {
        KnowledgeIndexJob job = jobService.getById(id);
        if (job == null || Boolean.TRUE.equals(job.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("knowledge.index-job.not-found"));
        accessService.requireReadable(job.getKnowledgeBaseId());
        return WebResponse.OK(job);
    }

    /**
     * 重试当前请求。
     */
    @PostMapping("/{id}/retry")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    public WebResponse<String> retry(@PathVariable String id) {
        KnowledgeIndexJob job = jobService.getById(id);
        if (job == null) throw new ServerException(404, I18nUtils.getMessage("knowledge.index-job.not-found"));
        accessService.requireWritable(job.getKnowledgeBaseId());
        KnowledgeDocument document = documentService.getById(job.getDocumentId());
        KnowledgeDocumentVersion version = versionService.getById(job.getDocumentVersionId());
        if (document == null || version == null)
            throw new ServerException(404, I18nUtils.getMessage("knowledge.document.version.not-found"));
        return WebResponse.OK(indexService.queueReindex(document, version, KnowledgeJobType.RETRY));
    }
}
