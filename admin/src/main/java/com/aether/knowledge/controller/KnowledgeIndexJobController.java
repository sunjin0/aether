package com.aether.knowledge.controller;

import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.entity.KnowledgeIndexJob;
import com.aether.knowledge.service.KnowledgeDocumentIndexService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeDocumentVersionService;
import com.aether.knowledge.service.KnowledgeIndexJobService;
import com.aether.permission.Permission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge/index-job")
@Permission(path = "/knowledge/document")
public class KnowledgeIndexJobController {
    private final KnowledgeIndexJobService jobService; private final KnowledgeDocumentService documentService;
    private final KnowledgeDocumentVersionService versionService; private final KnowledgeDocumentIndexService indexService;
    public KnowledgeIndexJobController(KnowledgeIndexJobService jobService, KnowledgeDocumentService documentService,
                                       KnowledgeDocumentVersionService versionService, KnowledgeDocumentIndexService indexService) {
        this.jobService=jobService; this.documentService=documentService; this.versionService=versionService; this.indexService=indexService;
    }
    @PostMapping("/list")
    public WebResponse<List<KnowledgeIndexJob>> list(@RequestBody(required=false) KnowledgeIndexJob query) {
        if (query == null) query = new KnowledgeIndexJob();
        Page<KnowledgeIndexJob> page = jobService.page(new Page<>(1, 50), Wrappers.lambdaQuery(KnowledgeIndexJob.class)
                .eq(query.getJobType()!=null, KnowledgeIndexJob::getJobType, query.getJobType())
                .eq(query.getKnowledgeBaseId()!=null, KnowledgeIndexJob::getKnowledgeBaseId, query.getKnowledgeBaseId())
                .eq(query.getDocumentId()!=null, KnowledgeIndexJob::getDocumentId, query.getDocumentId())
                .eq(query.getStatus()!=null, KnowledgeIndexJob::getStatus, query.getStatus()).eq(KnowledgeIndexJob::getDeleted, false).orderByDesc(KnowledgeIndexJob::getCreatedAt));
        return WebResponse.Page(page.getRecords(), page.getTotal());
    }
    @GetMapping("/{id}") public WebResponse<KnowledgeIndexJob> detail(@PathVariable String id) { return WebResponse.OK(jobService.getById(id)); }
    @PostMapping("/{id}/retry") @Permission(path="/knowledge/document", type=Permission.Type.Write)
    public WebResponse<String> retry(@PathVariable String id) {
        KnowledgeIndexJob job = jobService.getById(id); if (job == null) throw new ServerException(404, "index job not found");
        KnowledgeDocument document = documentService.getById(job.getDocumentId()); KnowledgeDocumentVersion version = versionService.getById(job.getDocumentVersionId());
        if (document == null || version == null) throw new ServerException(404, "document version not found");
        return WebResponse.OK(indexService.queueReindex(document, version, "retry"));
    }
}
