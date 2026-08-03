package com.aether.knowledge.controller;

import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.knowledge.entity.KnowledgeAiReview;
import com.aether.knowledge.entity.KnowledgeAiReviewIssue;
import com.aether.knowledge.service.KnowledgeAccessService;
import com.aether.knowledge.service.KnowledgeAiReviewIssueService;
import com.aether.knowledge.service.KnowledgeAiReviewRecordService;
import com.aether.knowledge.service.KnowledgeDocumentVersionService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeDocumentWorkflowService;
import com.aether.knowledge.vo.KnowledgeAiReviewDiffIssueVo;
import com.aether.knowledge.vo.KnowledgeAiReviewDiffVo;
import com.aether.knowledge.vo.KnowledgeAiReviewIssueAcceptResultVo;
import com.aether.knowledge.vo.KnowledgeAiReviewIssueAcceptVo;
import com.aether.knowledge.vo.KnowledgeAiReviewIssueBatchAcceptVo;
import com.aether.knowledge.vo.KnowledgeAiReviewIssueHandleVo;
import com.aether.knowledge.model.KnowledgeAiReviewSeverity;
import com.aether.knowledge.model.KnowledgeReviewStatus;
import com.aether.knowledge.model.KnowledgeAiReviewStatus;
import com.aether.knowledge.model.KnowledgeAiReviewIssueStatus;
import com.alibaba.fastjson2.JSONObject;
import com.aether.permission.Permission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

@RestController
@RequestMapping("/api/knowledge/ai-review")
@Permission(path = "/knowledge/document")
public class KnowledgeAiReviewController {
    private final KnowledgeAiReviewRecordService reviewService;
    private final KnowledgeAiReviewIssueService issueService;
    private final KnowledgeAccessService accessService;
    private final KnowledgeDocumentVersionService versionService;
    private final KnowledgeDocumentService documentService;
    private final KnowledgeDocumentWorkflowService workflowService;

    public KnowledgeAiReviewController(KnowledgeAiReviewRecordService reviewService,
                                       KnowledgeAiReviewIssueService issueService,
                                       KnowledgeAccessService accessService,
                                       KnowledgeDocumentVersionService versionService,
                                       KnowledgeDocumentService documentService,
                                       KnowledgeDocumentWorkflowService workflowService) {
        this.reviewService = reviewService;
        this.issueService = issueService;
        this.accessService = accessService;
        this.versionService = versionService;
        this.documentService = documentService;
        this.workflowService = workflowService;
    }

    @GetMapping("/{id}")
    public WebResponse<KnowledgeAiReview> detail(@PathVariable String id) {
        KnowledgeAiReview review = requireReview(id);
        return WebResponse.OK(review);
    }

    @GetMapping("/version/{versionId}/latest")
    public WebResponse<KnowledgeAiReview> latestByVersion(@PathVariable String versionId) {
        com.aether.knowledge.entity.KnowledgeDocumentVersion version = versionService.getById(versionId);
        if (version == null || Boolean.TRUE.equals(version.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("knowledge.document.version.not-found"));
        }
        com.aether.knowledge.entity.KnowledgeDocument document = documentService.getById(version.getKnowledgeDocumentId());
        if (document == null || Boolean.TRUE.equals(document.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("knowledge.document.not-found"));
        }
        accessService.requireReadable(document.getKnowledgeBaseId());
        KnowledgeAiReview review = reviewService.getOne(Wrappers.lambdaQuery(KnowledgeAiReview.class)
                .eq(KnowledgeAiReview::getDocumentVersionId, versionId)
                .eq(KnowledgeAiReview::getDeleted, false)
                .orderByDesc(KnowledgeAiReview::getCreatedAt)
                .last("LIMIT 1"), false);
        if (review == null) return WebResponse.OK((KnowledgeAiReview) null);
        return WebResponse.OK(review);
    }

    @GetMapping("/{id}/issues")
    public WebResponse<List<KnowledgeAiReviewIssue>> issues(@PathVariable String id) {
        requireReview(id);
        return WebResponse.OK(issueService.list(Wrappers.lambdaQuery(KnowledgeAiReviewIssue.class)
                .eq(KnowledgeAiReviewIssue::getAiReviewId, id)
                .eq(KnowledgeAiReviewIssue::getDeleted, false)
                .orderByAsc(KnowledgeAiReviewIssue::getCreatedAt)));
    }

    @GetMapping("/{id}/diff")
    public WebResponse<KnowledgeAiReviewDiffVo> diff(@PathVariable String id) {
        KnowledgeAiReview review = requireReview(id);
        com.aether.knowledge.entity.KnowledgeDocumentVersion version = versionService.getById(review.getDocumentVersionId());
        if (version == null || Boolean.TRUE.equals(version.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("knowledge.document.version.not-found"));
        }
        List<KnowledgeAiReviewIssue> sourceIssues = issueService.list(Wrappers.lambdaQuery(KnowledgeAiReviewIssue.class)
                .eq(KnowledgeAiReviewIssue::getAiReviewId, id)
                .eq(KnowledgeAiReviewIssue::getDeleted, false)
                .orderByAsc(KnowledgeAiReviewIssue::getCreatedAt));
        String originalContent = StringUtils.defaultIfBlank(review.getSourceContent(),
                StringUtils.defaultIfBlank(version.getOriginalContent(), version.getContent()));
        String proposedContent = buildProposedContent(originalContent, sourceIssues);
        KnowledgeAiReviewDiffVo result = new KnowledgeAiReviewDiffVo();
        result.setReviewId(review.getId());
        result.setDocumentId(review.getDocumentId());
        result.setDocumentVersionId(version.getId());
        result.setContentChecksum(version.getContentChecksum());
        result.setReviewStatus(version.getReviewStatus());
        result.setStale(!StringUtils.equals(review.getSourceChecksum(), version.getContentChecksum()));
        result.setOriginalContent(originalContent);
        result.setProposedContent(proposedContent);
        List<KnowledgeAiReviewDiffIssueVo> issues = new ArrayList<>();
        int pending = 0;
        int accepted = 0;
        int rejected = 0;
        int criticalPending = 0;
        for (KnowledgeAiReviewIssue issue : sourceIssues) {
            KnowledgeAiReviewDiffIssueVo item = new KnowledgeAiReviewDiffIssueVo();
            item.setId(issue.getId());
            item.setBlockId(issue.getBlockId());
            item.setIssueType(issue.getIssueType());
            item.setSeverity(issue.getSeverity());
            item.setMessage(issue.getMessage());
            item.setOriginalExcerpt(issue.getOriginalExcerpt());
            item.setSuggestedPatch(parsePatch(issue.getSuggestedPatch()));
            item.setHandleStatus(issue.getHandleStatus());
            int[] baseLines = lineRange(originalContent, issue.getOriginalExcerpt());
            int[] proposedLines = lineRange(proposedContent, issue.getOriginalExcerpt());
            item.setBaseStartLine(baseLines[0]);
            item.setBaseEndLine(baseLines[1]);
            item.setProposedStartLine(proposedLines[0]);
            item.setProposedEndLine(proposedLines[1]);
            issues.add(item);
            if (KnowledgeAiReviewIssueStatus.PENDING.equals(issue.getHandleStatus())) {
                pending++;
                if (KnowledgeAiReviewSeverity.CRITICAL.equalsIgnoreCase(issue.getSeverity())) criticalPending++;
            } else if (KnowledgeAiReviewIssueStatus.ACCEPTED.equals(issue.getHandleStatus())) {
                accepted++;
            } else {
                rejected++;
            }
        }
        result.setIssues(issues);
        result.setPendingCount(pending);
        result.setAcceptedCount(accepted);
        result.setRejectedCount(rejected);
        result.setCriticalPendingCount(criticalPending);
        return WebResponse.OK(result);
    }

    @PostMapping("/{reviewId}/issues/{issueId}/accept")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<KnowledgeAiReviewIssueAcceptResultVo> acceptIssue(@PathVariable String reviewId,
                                                                           @PathVariable String issueId,
                                                                           @RequestBody KnowledgeAiReviewIssueAcceptVo vo) {
        KnowledgeAiReview review = requireReview(reviewId);
        if (!KnowledgeAiReviewStatus.SUCCESS.equals(review.getStatus())) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.suggestions.not-ready"));
        }
        KnowledgeAiReviewIssue issue = requireIssue(issueId, reviewId);
        if (!KnowledgeAiReviewIssueStatus.PENDING.equals(issue.getHandleStatus())) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.issue.already-handled"));
        }
        if (parsePatch(issue.getSuggestedPatch()) == null) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.patch.not-applicable"));
        }
        com.aether.knowledge.entity.KnowledgeDocumentVersion version = versionService.getById(issue.getDocumentVersionId());
        if (version == null || Boolean.TRUE.equals(version.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("knowledge.document.version.not-found"));
        }
        accessService.requireWritable(review.getKnowledgeBaseId());
        if (!KnowledgeReviewStatus.AI_REVIEWED.equals(version.getReviewStatus())) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.suggestions.draft-required"));
        }
        if (!StringUtils.equals(review.getSourceChecksum(), version.getContentChecksum())) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.stale"));
        }
        if (StringUtils.isBlank(vo.getExpectedChecksum())) {
            throw new ServerException(400, I18nUtils.getMessage("knowledge.ai-review.draft-checksum.required"));
        }
        boolean updated = issueService.update(Wrappers.lambdaUpdate(KnowledgeAiReviewIssue.class)
                .eq(KnowledgeAiReviewIssue::getId, issueId)
                .eq(KnowledgeAiReviewIssue::getAiReviewId, reviewId)
                .eq(KnowledgeAiReviewIssue::getHandleStatus,
                        KnowledgeAiReviewIssueStatus.PENDING)
                .set(KnowledgeAiReviewIssue::getHandleStatus,
                        KnowledgeAiReviewIssueStatus.ACCEPTED)
                .set(KnowledgeAiReviewIssue::getHandledBy, accessService.currentAdminId())
                .set(KnowledgeAiReviewIssue::getHandledAt, System.currentTimeMillis())
                .set(KnowledgeAiReviewIssue::getHandleComment, vo.getComment())
                .set(KnowledgeAiReviewIssue::getAppliedContent, appliedContent(issue, vo.getReplacement()))
                .set(KnowledgeAiReviewIssue::getAppliedChecksum, null));
        if (!updated) throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.issue.already-handled"));
        KnowledgeAiReviewIssueAcceptResultVo result = new KnowledgeAiReviewIssueAcceptResultVo();
        result.setDocumentVersionId(version.getId());
        result.setContentChecksum(version.getContentChecksum());
        result.setReviewStatus(version.getReviewStatus());
        result.setIssueStatus(KnowledgeAiReviewIssueStatus.ACCEPTED);
        result.setRequiresAiReview(false);
        return WebResponse.OK(I18nUtils.getMessage("knowledge.ai-review.issue.accept.success"), result);
    }

    @PostMapping("/{reviewId}/issues/{issueId}/unaccept")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    public WebResponse<Void> unacceptIssue(@PathVariable String reviewId,
                                           @PathVariable String issueId,
                                           @RequestBody(required = false) KnowledgeAiReviewIssueHandleVo vo) {
        KnowledgeAiReview review = requireReview(reviewId);
        KnowledgeAiReviewIssue issue = requireIssue(issueId, reviewId);
        accessService.requireWritable(review.getKnowledgeBaseId());
        requireHandleableVersion(issue);
        if (!KnowledgeAiReviewIssueStatus.ACCEPTED.equals(issue.getHandleStatus())) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.issue.not-accepted"));
        }
        if (StringUtils.isNotBlank(issue.getAppliedChecksum())) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.issue.already-applied"));
        }
        boolean updated = issueService.update(Wrappers.lambdaUpdate(KnowledgeAiReviewIssue.class)
                .eq(KnowledgeAiReviewIssue::getId, issueId)
                .eq(KnowledgeAiReviewIssue::getAiReviewId, reviewId)
                .eq(KnowledgeAiReviewIssue::getHandleStatus,
                        KnowledgeAiReviewIssueStatus.ACCEPTED)
                .isNull(KnowledgeAiReviewIssue::getAppliedChecksum)
                .set(KnowledgeAiReviewIssue::getHandleStatus,
                        KnowledgeAiReviewIssueStatus.REJECTED)
                .set(KnowledgeAiReviewIssue::getHandledBy, accessService.currentAdminId())
                .set(KnowledgeAiReviewIssue::getHandledAt, System.currentTimeMillis())
                .set(KnowledgeAiReviewIssue::getHandleComment, vo == null ? null : vo.getComment())
                .set(KnowledgeAiReviewIssue::getAppliedContent, null));
        if (!updated) throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.issue.already-handled"));
        return WebResponse.OK(I18nUtils.getMessage("knowledge.ai-review.issue.unaccept.success"));
    }

    @PostMapping("/{reviewId}/issues/{issueId}/reject")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    public WebResponse<Void> rejectIssue(@PathVariable String reviewId,
                                         @PathVariable String issueId,
                                         @RequestBody(required = false) KnowledgeAiReviewIssueHandleVo vo) {
        KnowledgeAiReview review = requireReview(reviewId);
        KnowledgeAiReviewIssue issue = requireIssue(issueId, reviewId);
        accessService.requireWritable(review.getKnowledgeBaseId());
        requireHandleableVersion(issue);
        boolean updated = issueService.update(Wrappers.lambdaUpdate(KnowledgeAiReviewIssue.class)
                .eq(KnowledgeAiReviewIssue::getId, issueId)
                .eq(KnowledgeAiReviewIssue::getAiReviewId, reviewId)
                .eq(KnowledgeAiReviewIssue::getHandleStatus,
                        KnowledgeAiReviewIssueStatus.PENDING)
                .set(KnowledgeAiReviewIssue::getHandleStatus,
                        KnowledgeAiReviewIssueStatus.REJECTED)
                .set(KnowledgeAiReviewIssue::getHandledBy, accessService.currentAdminId())
                .set(KnowledgeAiReviewIssue::getHandledAt, System.currentTimeMillis())
                .set(KnowledgeAiReviewIssue::getHandleComment, vo == null ? null : vo.getComment()));
        if (!updated) throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.issue.already-handled"));
        return WebResponse.OK(I18nUtils.getMessage("knowledge.ai-review.issue.reject.success"));
    }

    @PostMapping("/{reviewId}/issues/accept-batch")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<KnowledgeAiReviewIssueAcceptResultVo> acceptIssues(@PathVariable String reviewId,
                                                                            @RequestBody KnowledgeAiReviewIssueBatchAcceptVo vo) {
        if (vo.getIssueIds() == null || vo.getIssueIds().isEmpty()) {
            throw new ServerException(400, I18nUtils.getMessage("knowledge.ai-review.issue.required"));
        }
        if (StringUtils.isBlank(vo.getExpectedChecksum())) {
            throw new ServerException(400, I18nUtils.getMessage("knowledge.ai-review.draft-checksum.required"));
        }
        KnowledgeAiReview review = requireReview(reviewId);
        if (!KnowledgeAiReviewStatus.SUCCESS.equals(review.getStatus())) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.suggestions.not-ready"));
        }
        com.aether.knowledge.entity.KnowledgeDocumentVersion version = versionService.getById(review.getDocumentVersionId());
        if (version == null || Boolean.TRUE.equals(version.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("knowledge.document.version.not-found"));
        }
        accessService.requireWritable(review.getKnowledgeBaseId());
        if (!KnowledgeReviewStatus.AI_REVIEWED.equals(version.getReviewStatus())
                || !StringUtils.equals(review.getSourceChecksum(), version.getContentChecksum())) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.stale"));
        }
        Set<String> distinctIds = new LinkedHashSet<>(vo.getIssueIds());
        if (distinctIds.size() != vo.getIssueIds().size()) {
            throw new ServerException(400, I18nUtils.getMessage("knowledge.ai-review.issue.duplicate-ids"));
        }
        List<KnowledgeAiReviewIssue> issues = new ArrayList<>();
        for (String issueId : distinctIds) {
            KnowledgeAiReviewIssue issue = requireIssue(issueId, reviewId);
            if (!KnowledgeAiReviewIssueStatus.PENDING.equals(issue.getHandleStatus())) {
                throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.issue.already-handled"));
            }
            if (parsePatch(issue.getSuggestedPatch()) == null) {
                throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.patch.not-applicable"));
            }
            if (KnowledgeAiReviewSeverity.CRITICAL.equalsIgnoreCase(issue.getSeverity())) {
                throw new ServerException(400, I18nUtils.getMessage("knowledge.ai-review.issue.critical-individual-acceptance"));
            }
            issues.add(issue);
        }
        long now = System.currentTimeMillis();
        for (KnowledgeAiReviewIssue issue : issues) {
            boolean updated = issueService.update(Wrappers.lambdaUpdate(KnowledgeAiReviewIssue.class)
                    .eq(KnowledgeAiReviewIssue::getId, issue.getId())
                    .eq(KnowledgeAiReviewIssue::getAiReviewId, reviewId)
                    .eq(KnowledgeAiReviewIssue::getHandleStatus,
                            KnowledgeAiReviewIssueStatus.PENDING)
                    .set(KnowledgeAiReviewIssue::getHandleStatus,
                            KnowledgeAiReviewIssueStatus.ACCEPTED)
                    .set(KnowledgeAiReviewIssue::getHandledBy, accessService.currentAdminId())
                    .set(KnowledgeAiReviewIssue::getHandledAt, now)
                    .set(KnowledgeAiReviewIssue::getHandleComment, vo.getComment())
                    .set(KnowledgeAiReviewIssue::getAppliedContent, appliedContent(issue, null))
                    .set(KnowledgeAiReviewIssue::getAppliedChecksum, null));
            if (!updated) throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.issue.already-handled"));
        }
        KnowledgeAiReviewIssueAcceptResultVo result = new KnowledgeAiReviewIssueAcceptResultVo();
        result.setDocumentVersionId(version.getId());
        result.setContentChecksum(version.getContentChecksum());
        result.setReviewStatus(version.getReviewStatus());
        result.setIssueStatus(KnowledgeAiReviewIssueStatus.ACCEPTED);
        result.setRequiresAiReview(false);
        return WebResponse.OK(I18nUtils.getMessage("knowledge.ai-review.issue.batch-accept.success"), result);
    }

    @PostMapping("/{reviewId}/issues/apply")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<KnowledgeAiReviewIssueAcceptResultVo> applyAcceptedIssues(@PathVariable String reviewId,
                                                                                   @RequestBody KnowledgeAiReviewIssueAcceptVo vo) {
        if (StringUtils.isBlank(vo.getExpectedChecksum())) {
            throw new ServerException(400, I18nUtils.getMessage("knowledge.ai-review.draft-checksum.required"));
        }
        KnowledgeAiReview review = requireReview(reviewId);
        if (!KnowledgeAiReviewStatus.SUCCESS.equals(review.getStatus())) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.suggestions.not-ready"));
        }
        com.aether.knowledge.entity.KnowledgeDocumentVersion version = versionService.getById(review.getDocumentVersionId());
        if (version == null || Boolean.TRUE.equals(version.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("knowledge.document.version.not-found"));
        }
        accessService.requireWritable(review.getKnowledgeBaseId());
        if (!KnowledgeReviewStatus.AI_REVIEWED.equals(version.getReviewStatus())
                || !StringUtils.equals(review.getSourceChecksum(), version.getContentChecksum())) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.stale"));
        }
        List<KnowledgeAiReviewIssue> acceptedIssues = issueService.list(Wrappers.lambdaQuery(KnowledgeAiReviewIssue.class)
                .eq(KnowledgeAiReviewIssue::getAiReviewId, reviewId)
                .eq(KnowledgeAiReviewIssue::getHandleStatus,
                        KnowledgeAiReviewIssueStatus.ACCEPTED)
                .eq(KnowledgeAiReviewIssue::getDeleted, false)
                .orderByAsc(KnowledgeAiReviewIssue::getCreatedAt));
        if (acceptedIssues.isEmpty()) {
            throw new ServerException(400, I18nUtils.getMessage("knowledge.ai-review.accepted-issue.required"));
        }
        String updatedContent = version.getContent();
        for (KnowledgeAiReviewIssue issue : acceptedIssues) {
            updatedContent = applyPatch(updatedContent, issue, issue.getAppliedContent());
        }
        com.aether.knowledge.entity.KnowledgeDocumentVersion updatedVersion = workflowService.applyAiReviewedChanges(
                version.getId(), updatedContent, vo.getExpectedChecksum());
        for (KnowledgeAiReviewIssue issue : acceptedIssues) {
            issueService.update(Wrappers.lambdaUpdate(KnowledgeAiReviewIssue.class)
                    .eq(KnowledgeAiReviewIssue::getId, issue.getId())
                    .eq(KnowledgeAiReviewIssue::getHandleStatus,
                            KnowledgeAiReviewIssueStatus.ACCEPTED)
                    .set(KnowledgeAiReviewIssue::getAppliedChecksum, updatedVersion.getContentChecksum()));
        }
        KnowledgeAiReviewIssueAcceptResultVo result = new KnowledgeAiReviewIssueAcceptResultVo();
        result.setDocumentVersionId(updatedVersion.getId());
        result.setContentChecksum(updatedVersion.getContentChecksum());
        result.setReviewStatus(updatedVersion.getReviewStatus());
        result.setIssueStatus(KnowledgeAiReviewIssueStatus.ACCEPTED);
        result.setRequiresAiReview(false);
        return WebResponse.OK(I18nUtils.getMessage("knowledge.ai-review.issue.apply.success"), result);
    }

    @PutMapping("/issue/{issueId}/handle")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    public WebResponse<Void> handleIssue(@PathVariable String issueId,
                                         @RequestBody KnowledgeAiReviewIssueHandleVo vo) {
        KnowledgeAiReviewIssue issue = issueService.getById(issueId);
        if (issue == null || Boolean.TRUE.equals(issue.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("knowledge.ai-review.issue.not-found"));
        }
        com.aether.knowledge.entity.KnowledgeDocumentVersion version = versionService.getById(issue.getDocumentVersionId());
        if (version == null || Boolean.TRUE.equals(version.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("knowledge.document.version.not-found"));
        }
        if (!KnowledgeReviewStatus.DRAFT.equals(version.getReviewStatus())
                && !KnowledgeReviewStatus.AI_REVIEWED.equals(version.getReviewStatus())) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.issue.handle-before-submission"));
        }
        KnowledgeAiReview review = requireReview(issue.getAiReviewId());
        accessService.requireWritable(review.getKnowledgeBaseId());
        String status = StringUtils.lowerCase(StringUtils.trimToEmpty(vo.getStatus()));
        if (!KnowledgeAiReviewIssueStatus.isManualResolution(status)) {
            throw new ServerException(400, I18nUtils.getMessage("knowledge.ai-review.issue.handle-status.invalid"));
        }
        boolean updated = issueService.update(Wrappers.lambdaUpdate(KnowledgeAiReviewIssue.class)
                .eq(KnowledgeAiReviewIssue::getId, issueId)
                .eq(KnowledgeAiReviewIssue::getHandleStatus,
                        KnowledgeAiReviewIssueStatus.PENDING)
                .set(KnowledgeAiReviewIssue::getHandleStatus, status)
                .set(KnowledgeAiReviewIssue::getHandledBy, accessService.currentAdminId())
                .set(KnowledgeAiReviewIssue::getHandledAt, System.currentTimeMillis())
                .set(KnowledgeAiReviewIssue::getHandleComment, vo.getComment()));
        if (!updated) throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.issue.already-handled"));
        return WebResponse.OK(I18nUtils.getMessage("knowledge.ai-review.issue.handle.success"));
    }

    private KnowledgeAiReviewIssue requireIssue(String issueId, String reviewId) {
        KnowledgeAiReviewIssue issue = issueService.getById(issueId);
        if (issue == null || Boolean.TRUE.equals(issue.getDeleted()) || !StringUtils.equals(reviewId, issue.getAiReviewId())) {
            throw new ServerException(404, I18nUtils.getMessage("knowledge.ai-review.issue.not-found"));
        }
        return issue;
    }

    private void requireHandleableVersion(KnowledgeAiReviewIssue issue) {
        com.aether.knowledge.entity.KnowledgeDocumentVersion version = versionService.getById(issue.getDocumentVersionId());
        if (version == null || Boolean.TRUE.equals(version.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("knowledge.document.version.not-found"));
        }
        if (!KnowledgeReviewStatus.DRAFT.equals(version.getReviewStatus())
                && !KnowledgeReviewStatus.AI_REVIEWED.equals(version.getReviewStatus())) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.issue.handle-before-submission"));
        }
    }

    private String buildProposedContent(String sourceContent, List<KnowledgeAiReviewIssue> issues) {
        String proposed = StringUtils.defaultString(sourceContent);
        for (KnowledgeAiReviewIssue issue : issues) {
            if (!KnowledgeAiReviewIssueStatus.PENDING.equals(issue.getHandleStatus())
                    && !KnowledgeAiReviewIssueStatus.ACCEPTED.equals(issue.getHandleStatus())) {
                continue;
            }
            try {
                proposed = applyPatch(proposed, issue,
                        KnowledgeAiReviewIssueStatus.ACCEPTED.equals(issue.getHandleStatus())
                        ? issue.getAppliedContent() : null);
            } catch (ServerException ignored) {
                // A conflicting or incomplete suggestion stays visible in the issue list but is not auto-previewed.
            }
        }
        return proposed;
    }

    private String appliedContent(KnowledgeAiReviewIssue issue, String manualReplacement) {
        JSONObject patch = parsePatch(issue.getSuggestedPatch());
        if (patch == null) return null;
        String operation = StringUtils.lowerCase(StringUtils.defaultIfBlank(patch.getString("operation"), "replace"));
        if ("delete".equals(operation)) return "";
        String replacement = StringUtils.defaultIfBlank(manualReplacement, patch.getString("replacement"));
        if ("set_heading".equals(operation) && StringUtils.isBlank(replacement)) {
            Integer level = patch.getInteger("level");
            String title = patch.getString("title");
            if (level != null && level >= 1 && level <= 6 && StringUtils.isNotBlank(title)) {
                replacement = StringUtils.repeat('#', level) + " " + title;
            }
        }
        return replacement;
    }

    private JSONObject parsePatch(String value) {
        if (StringUtils.isBlank(value)) return null;
        try {
            return JSONObject.parseObject(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String applyPatch(String content, KnowledgeAiReviewIssue issue, String manualReplacement) {
        JSONObject patch = parsePatch(issue.getSuggestedPatch());
        if (patch == null) throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.patch.not-applicable"));
        JSONObject target = patch.getJSONObject("target");
        String original = target == null ? null : target.getString("original");
        original = StringUtils.defaultIfBlank(original, issue.getOriginalExcerpt());
        if (StringUtils.isBlank(original)) throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.patch.target-required"));
        String document = StringUtils.defaultString(content);
        int firstIndex = document.indexOf(original);
        if (firstIndex < 0) throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.patch.target-mismatch"));
        if (document.indexOf(original, firstIndex + original.length()) >= 0) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.patch.target-ambiguous"));
        }
        String operation = StringUtils.lowerCase(StringUtils.defaultIfBlank(patch.getString("operation"), "replace"));
        String replacement = StringUtils.defaultIfBlank(manualReplacement, patch.getString("replacement"));
        if ("set_heading".equals(operation) && StringUtils.isBlank(replacement)) {
            Integer level = patch.getInteger("level");
            String title = patch.getString("title");
            if (level == null || level < 1 || level > 6 || StringUtils.isBlank(title)) {
                throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.patch.heading.invalid"));
            }
            replacement = StringUtils.repeat('#', level) + " " + title;
        }
        if ("delete".equals(operation)) replacement = "";
        if (("replace".equals(operation) || "insert_before".equals(operation)
                || "insert_after".equals(operation)) && StringUtils.isBlank(replacement)) {
            throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.patch.replacement.required"));
        }
        if ("replace".equals(operation) || "set_heading".equals(operation) || "delete".equals(operation)) {
            return document.substring(0, firstIndex) + replacement + document.substring(firstIndex + original.length());
        }
        if ("insert_before".equals(operation)) {
            return document.substring(0, firstIndex) + replacement + System.lineSeparator() + document.substring(firstIndex);
        }
        if ("insert_after".equals(operation)) {
            int endIndex = firstIndex + original.length();
            return document.substring(0, endIndex) + System.lineSeparator() + replacement + document.substring(endIndex);
        }
        throw new ServerException(400, I18nUtils.getMessage("knowledge.ai-review.patch.operation.unsupported"));
    }

    private int[] lineRange(String content, String excerpt) {
        if (StringUtils.isBlank(excerpt)) return new int[] {0, 0};
        int start = StringUtils.defaultString(content).indexOf(excerpt);
        if (start < 0) return new int[] {0, 0};
        int startLine = 1;
        for (int i = 0; i < start; i++) if (content.charAt(i) == '\n') startLine++;
        int endLine = startLine;
        for (int i = start; i < start + excerpt.length(); i++) if (content.charAt(i) == '\n') endLine++;
        return new int[] {startLine, endLine};
    }

    private KnowledgeAiReview requireReview(String id) {
        KnowledgeAiReview review = reviewService.getById(id);
        if (review == null || Boolean.TRUE.equals(review.getDeleted())) throw new ServerException(404, I18nUtils.getMessage("knowledge.ai-review.not-found"));
        accessService.requireReadable(review.getKnowledgeBaseId());
        return review;
    }
}
