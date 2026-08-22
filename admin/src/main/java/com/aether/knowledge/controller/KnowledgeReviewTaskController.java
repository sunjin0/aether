package com.aether.knowledge.controller;

import com.aether.entity.WebResponse;
import com.aether.i18n.I18nUtils;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.service.KnowledgeDocumentWorkflowService;
import com.aether.knowledge.service.KnowledgeReviewTaskQueryService;
import com.aether.knowledge.vo.KnowledgeDraftUpdateVo;
import com.aether.knowledge.vo.KnowledgeReviewDecisionVo;
import com.aether.knowledge.vo.KnowledgeReviewTaskQueryVo;
import com.aether.knowledge.vo.KnowledgeReviewTaskVo;
import com.aether.knowledge.vo.KnowledgeReviewTaskDetailVo;
import com.aether.permission.Permission;
import io.swagger.annotations.ApiOperation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * 提供知识库审核任务相关的 REST 接口。
 */
@RestController
@RequestMapping("/api/knowledge/review-task")
@Permission(path = "/knowledge/document")
public class KnowledgeReviewTaskController {
    private final KnowledgeReviewTaskQueryService queryService;
    private final KnowledgeDocumentWorkflowService workflowService;

    /**
     * 创建 {@code KnowledgeReviewTaskController} 实例。
     */
    public KnowledgeReviewTaskController(KnowledgeReviewTaskQueryService queryService,
                                         KnowledgeDocumentWorkflowService workflowService) {
        this.queryService = queryService;
        this.workflowService = workflowService;
    }

    /**
     * 查询当前请求。
     */
    @PostMapping("/list")
    public WebResponse<List<KnowledgeReviewTaskVo>> list(@RequestBody(required = false) KnowledgeReviewTaskQueryVo query) {
        com.baomidou.mybatisplus.core.metadata.IPage<KnowledgeReviewTaskVo> page = queryService.list(query);
        return WebResponse.Page(page.getRecords(), page.getTotal());
    }

    /**
     * 详情当前请求。
     */
    @GetMapping("/{id}")
    public WebResponse<KnowledgeReviewTaskDetailVo> detail(@PathVariable String id) {
        return WebResponse.OK(queryService.detail(id));
    }

    /**
     * 处理claim。
     */
    @PostMapping("/{id}/claim")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    public WebResponse<Void> claim(@PathVariable String id) {
        workflowService.claim(id);
        return WebResponse.OK(I18nUtils.getMessage("knowledge.review-task.claimed"));
    }

    /**
     * 审批通过当前请求。
     */
    @ApiOperation(value = "审批审核", notes = "审批审核")
    @PostMapping("/{id}/approve")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    public WebResponse<String> approve(@PathVariable String id,
                                       @RequestBody(required = false) KnowledgeReviewDecisionVo vo) {
        String indexJobId = workflowService.approve(id, vo == null ? null : vo.getComment());
        return WebResponse.OK(I18nUtils.getMessage("knowledge.review-task.approved"), indexJobId);
    }

    /**
     * 拒绝当前请求。
     */
    @ApiOperation(value = "拒绝审核", notes = "拒绝审核")
    @PostMapping("/{id}/reject")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    public WebResponse<Void> reject(@PathVariable String id, @RequestBody KnowledgeReviewDecisionVo vo) {
        workflowService.reject(id, vo == null ? null : vo.getComment());
        return WebResponse.OK(I18nUtils.getMessage("knowledge.review-task.rejected"));
    }

    /**
     * 编辑审核内容。
     */
    @ApiOperation("编辑审核内容")
    @PutMapping("/{id}/edit")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<KnowledgeDocumentVersion> editContent(@PathVariable @NotBlank String id,
                                                             @RequestBody KnowledgeDraftUpdateVo vo) {
        KnowledgeDocumentVersion updated = workflowService.editReviewContent(id, vo.getContent(), vo.getExpectedChecksum());
        return WebResponse.OK(I18nUtils.getMessage("knowledge.review-task.content.edited"), updated);
    }
}
