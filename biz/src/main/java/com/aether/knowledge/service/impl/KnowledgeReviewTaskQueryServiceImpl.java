package com.aether.knowledge.service.impl;

import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.knowledge.entity.KnowledgeAiReview;
import com.aether.knowledge.entity.KnowledgeAiReviewIssue;
import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.entity.KnowledgeReviewActionLog;
import com.aether.knowledge.entity.KnowledgeReviewTask;
import com.aether.knowledge.model.KnowledgeReviewTaskStatus;
import com.aether.knowledge.service.KnowledgeAccessService;
import com.aether.knowledge.service.KnowledgeAiReviewIssueService;
import com.aether.knowledge.service.KnowledgeAiReviewRecordService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeDocumentVersionService;
import com.aether.knowledge.service.KnowledgeReviewActionLogService;
import com.aether.knowledge.service.KnowledgeReviewTaskQueryService;
import com.aether.knowledge.service.KnowledgeReviewTaskService;
import com.aether.knowledge.vo.KnowledgeDocumentVo;
import com.aether.knowledge.vo.KnowledgeReviewTaskDetailVo;
import com.aether.knowledge.vo.KnowledgeReviewTaskQueryVo;
import com.aether.knowledge.vo.KnowledgeReviewTaskVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class KnowledgeReviewTaskQueryServiceImpl implements KnowledgeReviewTaskQueryService {
    private final KnowledgeReviewTaskService taskService;
    private final KnowledgeAccessService accessService;
    private final KnowledgeDocumentService documentService;
    private final KnowledgeDocumentVersionService versionService;
    private final KnowledgeAiReviewRecordService aiReviewService;
    private final KnowledgeAiReviewIssueService aiIssueService;
    private final KnowledgeReviewActionLogService actionLogService;

    public KnowledgeReviewTaskQueryServiceImpl(KnowledgeReviewTaskService taskService,
                                               KnowledgeAccessService accessService,
                                               KnowledgeDocumentService documentService,
                                               KnowledgeDocumentVersionService versionService,
                                               KnowledgeAiReviewRecordService aiReviewService,
                                               KnowledgeAiReviewIssueService aiIssueService,
                                               KnowledgeReviewActionLogService actionLogService) {
        this.taskService = taskService;
        this.accessService = accessService;
        this.documentService = documentService;
        this.versionService = versionService;
        this.aiReviewService = aiReviewService;
        this.aiIssueService = aiIssueService;
        this.actionLogService = actionLogService;
    }

    @Override
    public IPage<KnowledgeReviewTaskVo> list(KnowledgeReviewTaskQueryVo request) {
        KnowledgeReviewTaskQueryVo query = request == null ? new KnowledgeReviewTaskQueryVo() : request;
        String taskStatus = normalizeStatus(query.getStatus());
        List<String> readableIds = accessService.readableKnowledgeBaseIds();
        String currentAdminId = accessService.currentAdminId();
        long current = query.getCurrent() == null || query.getCurrent() < 1 ? 1 : query.getCurrent();
        long pageSize = query.getPageSize() == null ? 20 : Math.max(1, Math.min(100, query.getPageSize()));
        if (readableIds.isEmpty()) {
            return new Page<KnowledgeReviewTaskVo>(current, pageSize, 0)
                    .setRecords(Collections.emptyList());
        }
        Page<KnowledgeReviewTask> page = taskService.page(new Page<>(current, pageSize),
                Wrappers.lambdaQuery(KnowledgeReviewTask.class)
                        .in(KnowledgeReviewTask::getKnowledgeBaseId, readableIds)
                        .eq(StringUtils.isNotBlank(query.getKnowledgeBaseId()),
                                KnowledgeReviewTask::getKnowledgeBaseId, query.getKnowledgeBaseId())
                        .eq(StringUtils.isNotBlank(query.getDocumentId()),
                                KnowledgeReviewTask::getDocumentId, query.getDocumentId())
                        .eq(StringUtils.isNotBlank(taskStatus), KnowledgeReviewTask::getStatus, taskStatus)
                        .eq("submittedByMe".equals(query.getView()),
                                KnowledgeReviewTask::getSubmitterId, currentAdminId)
                        .eq("reviewedByMe".equals(query.getView()),
                                KnowledgeReviewTask::getReviewerId, currentAdminId)
                        .and("available".equals(query.getView()), nested -> nested
                                .and(pending -> pending.eq(KnowledgeReviewTask::getStatus,
                                                KnowledgeReviewTaskStatus.PENDING)
                                        .and(assignee -> assignee.isNull(KnowledgeReviewTask::getReviewerId)
                                                .or().eq(KnowledgeReviewTask::getReviewerId, currentAdminId)))
                                .or(group -> group.eq(KnowledgeReviewTask::getStatus,
                                                KnowledgeReviewTaskStatus.CLAIMED)
                                        .eq(KnowledgeReviewTask::getReviewerId, currentAdminId)))
                        .eq(KnowledgeReviewTask::getDeleted, false)
                        .orderByDesc(KnowledgeReviewTask::getSubmittedAt));
        Map<String, KnowledgeDocument> documents = loadDocuments(page.getRecords());
        Map<String, KnowledgeDocumentVersion> versions = loadVersions(page.getRecords());
        List<KnowledgeReviewTaskVo> records = page.getRecords().stream()
                .map(task -> toTaskVo(task, documents.get(task.getDocumentId()),
                        versions.get(task.getDocumentVersionId())))
                .collect(Collectors.toList());
        return new Page<KnowledgeReviewTaskVo>(page.getCurrent(), page.getSize(), page.getTotal())
                .setRecords(records);
    }

    @Override
    public KnowledgeReviewTaskDetailVo detail(String taskId) {
        KnowledgeReviewTask task = taskService.getById(taskId);
        if (task == null || Boolean.TRUE.equals(task.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("knowledge.review-task.not-found"));
        }
        accessService.requireReadable(task.getKnowledgeBaseId());
        KnowledgeDocument document = documentService.getById(task.getDocumentId());
        KnowledgeDocumentVersion version = versionService.getById(task.getDocumentVersionId());
        if (document == null || version == null) {
            throw new ServerException(404,
                    I18nUtils.getMessage("knowledge.review-task.document-version-not-found"));
        }
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
        List<KnowledgeReviewActionLog> logs = actionLogService.list(
                Wrappers.lambdaQuery(KnowledgeReviewActionLog.class)
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
        result.setIssues(issues);
        result.setActionLogs(logs);
        return result;
    }

    private String normalizeStatus(String status) {
        String normalized = StringUtils.lowerCase(StringUtils.trimToEmpty(status));
        if (StringUtils.isNotBlank(normalized) && !KnowledgeReviewTaskStatus.isValid(normalized)) {
            throw new ServerException(400,
                    I18nUtils.getMessage("knowledge.review-task.status.invalid"));
        }
        return normalized;
    }

    private Map<String, KnowledgeDocument> loadDocuments(List<KnowledgeReviewTask> tasks) {
        if (tasks.isEmpty()) return Collections.emptyMap();
        return documentService.listByIds(tasks.stream()
                        .map(KnowledgeReviewTask::getDocumentId)
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));
    }

    private Map<String, KnowledgeDocumentVersion> loadVersions(List<KnowledgeReviewTask> tasks) {
        if (tasks.isEmpty()) return Collections.emptyMap();
        return versionService.listByIds(tasks.stream()
                        .map(KnowledgeReviewTask::getDocumentVersionId)
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(KnowledgeDocumentVersion::getId, Function.identity()));
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
}
