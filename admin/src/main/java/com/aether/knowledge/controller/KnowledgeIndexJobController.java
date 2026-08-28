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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import lombok.Data;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * 提供知识库索引Job相关的 REST 接口。
 */
@RestController
@Api(tags = "知识库索引任务 API")
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
    @ApiOperation("查询知识库索引任务列表")
    @PostMapping("/list")
    public WebResponse<List<KnowledgeIndexJob>> list(@RequestBody(required = false) ListRequest request) {
        KnowledgeIndexJobQueryVo query = request == null ? null : request.toQuery();
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
    @ApiOperation("查询知识库索引任务详情")
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
    @ApiOperation("重试知识库索引任务")
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
        String retryJobId = indexService.queueReindex(document, version, KnowledgeJobType.RETRY);
        return WebResponse.OK(I18nUtils.getMessage("knowledge.index-job.retry.queued"), retryJobId);
    }

    @Data @ApiModel("索引任务列表请求")
    public static class ListRequest {
        @ApiModelProperty(value = "页码", example = "1") private Long current;
        @ApiModelProperty(value = "每页数量", example = "20") private Long pageSize;
        @ApiModelProperty(value = "任务类型", example = "REINDEX") private String jobType;
        @ApiModelProperty(value = "知识库 ID", example = "kb-001") private String knowledgeBaseId;
        @ApiModelProperty(value = "文档 ID", example = "doc-001") private String documentId;
        @ApiModelProperty(value = "任务状态", example = "SUCCEEDED") private String status;
        public KnowledgeIndexJobQueryVo toQuery() {
            KnowledgeIndexJobQueryVo query = new KnowledgeIndexJobQueryVo();
            query.setCurrent(current); query.setPageSize(pageSize); query.setJobType(jobType); query.setKnowledgeBaseId(knowledgeBaseId);
            query.setDocumentId(documentId); query.setStatus(status); return query;
        }
    }
}
