package com.aether.knowledge.controller;

import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.knowledge.entity.KnowledgeReviewTask;
import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.entity.KnowledgeAiReview;
import com.aether.knowledge.entity.KnowledgeAiReviewIssue;
import com.aether.knowledge.entity.KnowledgeReviewActionLog;
import com.aether.knowledge.service.KnowledgeAccessService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeDocumentVersionService;
import com.aether.knowledge.service.KnowledgeAiReviewRecordService;
import com.aether.knowledge.service.KnowledgeAiReviewIssueService;
import com.aether.knowledge.service.KnowledgeReviewActionLogService;
import com.aether.knowledge.service.KnowledgeDocumentWorkflowService;
import com.aether.knowledge.service.KnowledgeReviewTaskService;
import com.aether.knowledge.vo.KnowledgeReviewDecisionVo;
import com.aether.knowledge.vo.KnowledgeReviewTaskQueryVo;
import com.aether.knowledge.vo.KnowledgeReviewTaskVo;
import com.aether.knowledge.vo.KnowledgeReviewTaskDetailVo;
import com.aether.knowledge.vo.KnowledgeDocumentVo;
import com.aether.permission.Permission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.BeanUtils;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/knowledge/review-task")
@Permission(path = "/knowledge/document")
public class KnowledgeReviewTaskController {
    private final KnowledgeReviewTaskService taskService;
    private final KnowledgeDocumentWorkflowService workflowService;
    private final KnowledgeAccessService accessService;
    private final KnowledgeDocumentService documentService;
    private final KnowledgeDocumentVersionService versionService;
    private final KnowledgeAiReviewRecordService aiReviewService;
    private final KnowledgeAiReviewIssueService aiIssueService;
    private final KnowledgeReviewActionLogService actionLogService;

    public KnowledgeReviewTaskController(KnowledgeReviewTaskService taskService,
                                         KnowledgeDocumentWorkflowService workflowService,
                                         KnowledgeAccessService accessService,
                                         KnowledgeDocumentService documentService,
                                         KnowledgeDocumentVersionService versionService,
                                         KnowledgeAiReviewRecordService aiReviewService,
                                         KnowledgeAiReviewIssueService aiIssueService,
                                         KnowledgeReviewActionLogService actionLogService) {
        this.taskService = taskService;
        this.workflowService = workflowService;
        this.accessService = accessService;
        this.documentService = documentService;
        this.versionService = versionService;
        this.aiReviewService = aiReviewService;
        this.aiIssueService = aiIssueService;
        this.actionLogService = actionLogService;
    }

    @PostMapping("/list")
    public WebResponse<List<KnowledgeReviewTaskVo>> list(@RequestBody(required = false) KnowledgeReviewTaskQueryVo query) {
        if (query == null) query = new KnowledgeReviewTaskQueryVo();
        List<String> readableIds = accessService.readableKnowledgeBaseIds();
        String currentAdminId = accessService.currentAdminId();
        long current = query.getCurrent() == null || query.getCurrent() < 1 ? 1 : query.getCurrent();
        long pageSize = query.getPageSize() == null ? 20 : Math.max(1, Math.min(100, query.getPageSize()));
        Page<KnowledgeReviewTask> page = taskService.page(new Page<>(current, pageSize),
                Wrappers.lambdaQuery(KnowledgeReviewTask.class)
                        .in(!readableIds.isEmpty(), KnowledgeReviewTask::getKnowledgeBaseId, readableIds)
                        .apply(readableIds.isEmpty(), "1 = 0")
                        .eq(StringUtils.isNotBlank(query.getKnowledgeBaseId()), KnowledgeReviewTask::getKnowledgeBaseId, query.getKnowledgeBaseId())
                        .eq(StringUtils.isNotBlank(query.getDocumentId()), KnowledgeReviewTask::getDocumentId, query.getDocumentId())
                        .eq(StringUtils.isNotBlank(query.getStatus()), KnowledgeReviewTask::getStatus, query.getStatus())
                        .eq("submittedByMe".equals(query.getView()), KnowledgeReviewTask::getSubmitterId, currentAdminId)
                        .eq("reviewedByMe".equals(query.getView()), KnowledgeReviewTask::getReviewerId, currentAdminId)
                        .and("available".equals(query.getView()), nested -> nested
                                .eq(KnowledgeReviewTask::getStatus, "pending")
                                .or(group -> group.eq(KnowledgeReviewTask::getStatus, "claimed")
                                        .eq(KnowledgeReviewTask::getReviewerId, currentAdminId)))
                        .eq(KnowledgeReviewTask::getDeleted, false)
                        .orderByDesc(KnowledgeReviewTask::getSubmittedAt));
        Map<String, KnowledgeDocument> documents = page.getRecords().isEmpty() ? Collections.emptyMap()
                : documentService.listByIds(page.getRecords().stream().map(KnowledgeReviewTask::getDocumentId)
                        .collect(Collectors.toSet())).stream()
                        .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));
        Map<String, KnowledgeDocumentVersion> versions = page.getRecords().isEmpty() ? Collections.emptyMap()
                : versionService.listByIds(page.getRecords().stream().map(KnowledgeReviewTask::getDocumentVersionId)
                        .collect(Collectors.toSet())).stream()
                        .collect(Collectors.toMap(KnowledgeDocumentVersion::getId, Function.identity()));
        List<KnowledgeReviewTaskVo> result = page.getRecords().stream()
                .map(task -> toTaskVo(task, documents.get(task.getDocumentId()), versions.get(task.getDocumentVersionId())))
                .collect(Collectors.toList());
        return WebResponse.Page(result, page.getTotal());
    }

    @GetMapping("/{id}")
    public WebResponse<KnowledgeReviewTaskDetailVo> detail(@PathVariable String id) {
        KnowledgeReviewTask task = taskService.getById(id);
        if (task == null || Boolean.TRUE.equals(task.getDeleted())) throw new ServerException(404, I18nUtils.getMessage("knowledge.review-task.not-found"));
        accessService.requireReadable(task.getKnowledgeBaseId());
        KnowledgeDocument document = documentService.getById(task.getDocumentId());
        KnowledgeDocumentVersion version = versionService.getById(task.getDocumentVersionId());
        if (document == null || version == null) throw new ServerException(404, I18nUtils.getMessage("knowledge.review-task.document-version-not-found"));
        KnowledgeAiReview aiReview = aiReviewService.getOne(Wrappers.lambdaQuery(KnowledgeAiReview.class)
                .eq(KnowledgeAiReview::getDocumentVersionId, version.getId())
                .eq(KnowledgeAiReview::getDeleted, false)
                .orderByDesc(KnowledgeAiReview::getCreatedAt)
                .last("LIMIT 1"), false);
        List<KnowledgeAiReviewIssue> issues = aiReview == null ? Collections.emptyList()
                : aiIssueService.list(Wrappers.lambdaQuery(KnowledgeAiReviewIssue.class)
                        .eq(KnowledgeAiReviewIssue::getAiReviewId, aiReview.getId())
                        .eq(KnowledgeAiReviewIssue::getDeleted, false)
                        .orderByAsc(KnowledgeAiReviewIssue::getCreatedAt));
        List<KnowledgeReviewActionLog> logs = actionLogService.list(Wrappers.lambdaQuery(KnowledgeReviewActionLog.class)
                .eq(KnowledgeReviewActionLog::getDocumentVersionId, version.getId())
                .eq(KnowledgeReviewActionLog::getDeleted, false)
                .orderByAsc(KnowledgeReviewActionLog::getCreatedAt));
        KnowledgeReviewTaskDetailVo result = new KnowledgeReviewTaskDetailVo();
        BeanUtils.copyProperties(toTaskVo(task, document, version), result);
        KnowledgeDocumentVo documentVo = new KnowledgeDocumentVo();
        BeanUtils.copyProperties(document, documentVo);
        result.setDocument(documentVo);
        result.setVersion(version);
        result.setAiReview(aiReview);
        result.setAiIssues(issues);
        result.setActionLogs(logs);
        return WebResponse.OK(result);
    }

    private KnowledgeReviewTaskVo toTaskVo(KnowledgeReviewTask task, KnowledgeDocument document,
                                            KnowledgeDocumentVersion version) {
        KnowledgeReviewTaskVo vo = new KnowledgeReviewTaskVo();
        BeanUtils.copyProperties(task, vo);
        if (document != null) vo.setDocumentTitle(document.getTitle());
        if (version != null) {
            vo.setVersionNo(version.getVersionNo());
            vo.setVersionReviewStatus(version.getReviewStatus());
        }
        return vo;
    }

    @PostMapping("/{id}/claim")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    public WebResponse<Void> claim(@PathVariable String id) {
        workflowService.claim(id);
        return WebResponse.OK(I18nUtils.getMessage("knowledge.review-task.claimed"));
    }

    @PostMapping("/{id}/approve")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    public WebResponse<String> approve(@PathVariable String id,
                                       @RequestBody(required = false) KnowledgeReviewDecisionVo vo) {
        return WebResponse.OK(workflowService.approve(id, vo == null ? null : vo.getComment()));
    }

    @PostMapping("/{id}/reject")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    public WebResponse<Void> reject(@PathVariable String id, @RequestBody KnowledgeReviewDecisionVo vo) {
        workflowService.reject(id, vo == null ? null : vo.getComment());
        return WebResponse.OK(I18nUtils.getMessage("knowledge.review-task.rejected"));
    }
}
