package com.aether.knowledge.controller;

import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.knowledge.entity.KnowledgeAiReview;
import com.aether.knowledge.entity.KnowledgeAiReviewIssue;
import com.aether.knowledge.service.KnowledgeAccessService;
import com.aether.knowledge.service.KnowledgeAiReviewIssueService;
import com.aether.knowledge.service.KnowledgeAiReviewRecordService;
import com.aether.knowledge.service.KnowledgeDocumentVersionService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.vo.KnowledgeAiReviewIssueHandleVo;
import com.aether.knowledge.model.KnowledgeReviewStatus;
import com.aether.permission.Permission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Arrays;

@RestController
@RequestMapping("/api/knowledge/ai-review")
@Permission(path = "/knowledge/document")
public class KnowledgeAiReviewController {
    private final KnowledgeAiReviewRecordService reviewService;
    private final KnowledgeAiReviewIssueService issueService;
    private final KnowledgeAccessService accessService;
    private final KnowledgeDocumentVersionService versionService;
    private final KnowledgeDocumentService documentService;

    public KnowledgeAiReviewController(KnowledgeAiReviewRecordService reviewService,
                                       KnowledgeAiReviewIssueService issueService,
                                       KnowledgeAccessService accessService,
                                       KnowledgeDocumentVersionService versionService,
                                       KnowledgeDocumentService documentService) {
        this.reviewService = reviewService;
        this.issueService = issueService;
        this.accessService = accessService;
        this.versionService = versionService;
        this.documentService = documentService;
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
            throw new ServerException(404, "document version not found");
        }
        com.aether.knowledge.entity.KnowledgeDocument document = documentService.getById(version.getKnowledgeDocumentId());
        if (document == null || Boolean.TRUE.equals(document.getDeleted())) {
            throw new ServerException(404, "knowledge document not found");
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

    @PutMapping("/issue/{issueId}/handle")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    public WebResponse<Void> handleIssue(@PathVariable String issueId,
                                         @RequestBody KnowledgeAiReviewIssueHandleVo vo) {
        KnowledgeAiReviewIssue issue = issueService.getById(issueId);
        if (issue == null || Boolean.TRUE.equals(issue.getDeleted())) {
            throw new ServerException(404, "AI review issue not found");
        }
        com.aether.knowledge.entity.KnowledgeDocumentVersion version = versionService.getById(issue.getDocumentVersionId());
        if (version == null || Boolean.TRUE.equals(version.getDeleted())) {
            throw new ServerException(404, "document version not found");
        }
        if (!KnowledgeReviewStatus.DRAFT.equals(version.getReviewStatus())
                && !KnowledgeReviewStatus.AI_REVIEWED.equals(version.getReviewStatus())) {
            throw new ServerException(409, "issues can only be handled before submission");
        }
        KnowledgeAiReview review = requireReview(issue.getAiReviewId());
        accessService.requireWritable(review.getKnowledgeBaseId());
        String status = StringUtils.lowerCase(StringUtils.trimToEmpty(vo.getStatus()));
        if (!Arrays.asList("rejected", "manually_fixed", "ignored").contains(status)) {
            throw new ServerException(400, "invalid issue handle status");
        }
        boolean updated = issueService.update(Wrappers.lambdaUpdate(KnowledgeAiReviewIssue.class)
                .eq(KnowledgeAiReviewIssue::getId, issueId)
                .eq(KnowledgeAiReviewIssue::getHandleStatus, "pending")
                .set(KnowledgeAiReviewIssue::getHandleStatus, status)
                .set(KnowledgeAiReviewIssue::getHandledBy, accessService.currentAdminId())
                .set(KnowledgeAiReviewIssue::getHandledAt, System.currentTimeMillis())
                .set(KnowledgeAiReviewIssue::getHandleComment, vo.getComment()));
        if (!updated) throw new ServerException(409, "AI review issue has already been handled");
        return WebResponse.OK((Void) null);
    }

    private KnowledgeAiReview requireReview(String id) {
        KnowledgeAiReview review = reviewService.getById(id);
        if (review == null || Boolean.TRUE.equals(review.getDeleted())) throw new ServerException(404, "AI review not found");
        accessService.requireReadable(review.getKnowledgeBaseId());
        return review;
    }
}
