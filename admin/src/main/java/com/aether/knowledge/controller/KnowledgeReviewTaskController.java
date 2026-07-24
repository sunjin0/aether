package com.aether.knowledge.controller;

import com.aether.entity.WebResponse;
import com.aether.i18n.I18nUtils;
import com.aether.knowledge.service.KnowledgeDocumentWorkflowService;
import com.aether.knowledge.service.KnowledgeReviewTaskQueryService;
import com.aether.knowledge.vo.KnowledgeReviewDecisionVo;
import com.aether.knowledge.vo.KnowledgeReviewTaskQueryVo;
import com.aether.knowledge.vo.KnowledgeReviewTaskVo;
import com.aether.knowledge.vo.KnowledgeReviewTaskDetailVo;
import com.aether.permission.Permission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge/review-task")
@Permission(path = "/knowledge/document")
public class KnowledgeReviewTaskController {
    private final KnowledgeReviewTaskQueryService queryService;
    private final KnowledgeDocumentWorkflowService workflowService;

    public KnowledgeReviewTaskController(KnowledgeReviewTaskQueryService queryService,
                                         KnowledgeDocumentWorkflowService workflowService) {
        this.queryService = queryService;
        this.workflowService = workflowService;
    }

    @PostMapping("/list")
    public WebResponse<List<KnowledgeReviewTaskVo>> list(@RequestBody(required = false) KnowledgeReviewTaskQueryVo query) {
        IPage<KnowledgeReviewTaskVo> page = queryService.list(query);
        return WebResponse.Page(page.getRecords(), page.getTotal());
    }

    @GetMapping("/{id}")
    public WebResponse<KnowledgeReviewTaskDetailVo> detail(@PathVariable String id) {
        return WebResponse.OK(queryService.detail(id));
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
