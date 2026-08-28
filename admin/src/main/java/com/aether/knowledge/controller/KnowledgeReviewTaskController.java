package com.aether.knowledge.controller;

import com.aether.entity.WebResponse;
import com.aether.i18n.I18nUtils;
import com.aether.knowledge.entity.KnowledgeDocumentVersion;
import com.aether.knowledge.service.KnowledgeDocumentWorkflowService;
import com.aether.knowledge.service.KnowledgeReviewTaskQueryService;
import com.aether.knowledge.vo.KnowledgeReviewTaskQueryVo;
import com.aether.knowledge.vo.KnowledgeReviewTaskVo;
import com.aether.knowledge.vo.KnowledgeReviewTaskDetailVo;
import com.aether.permission.Permission;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import lombok.Data;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * 提供知识库审核任务相关的 REST 接口。
 */
@RestController
@Api(tags = "知识库人工审核任务 API")
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
    @ApiOperation("查询审核任务列表")
    @PostMapping("/list")
    public WebResponse<List<KnowledgeReviewTaskVo>> list(@RequestBody(required = false) ListRequest request) {
        KnowledgeReviewTaskQueryVo query = request == null ? null : request.toQuery();
        com.baomidou.mybatisplus.core.metadata.IPage<KnowledgeReviewTaskVo> page = queryService.list(query);
        return WebResponse.Page(page.getRecords(), page.getTotal());
    }

    /**
     * 详情当前请求。
     */
    @ApiOperation("查询审核任务详情")
    @GetMapping("/{id}")
    public WebResponse<KnowledgeReviewTaskDetailVo> detail(@PathVariable String id) {
        return WebResponse.OK(queryService.detail(id));
    }

    /**
     * 处理claim。
     */
    @ApiOperation("认领审核任务")
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
                                        @RequestBody(required = false) ApproveRequest request) {
        String indexJobId = workflowService.approve(id, request == null ? null : request.getComment());
        return WebResponse.OK(I18nUtils.getMessage("knowledge.review-task.approved"), indexJobId);
    }

    /**
     * 拒绝当前请求。
     */
    @ApiOperation(value = "拒绝审核", notes = "拒绝审核")
    @PostMapping("/{id}/reject")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    public WebResponse<Void> reject(@PathVariable String id, @RequestBody RejectRequest request) {
        workflowService.reject(id, request == null ? null : request.getComment());
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
                                                              @RequestBody EditContentRequest request) {
        KnowledgeDocumentVersion updated = workflowService.editReviewContent(id, request.getContent(), request.getExpectedChecksum());
        return WebResponse.OK(I18nUtils.getMessage("knowledge.review-task.content.edited"), updated);
    }

    @Data @ApiModel("审核任务列表请求")
    public static class ListRequest {
        @ApiModelProperty(value = "页码", example = "1") private Long current;
        @ApiModelProperty(value = "每页数量", example = "20") private Long pageSize;
        @ApiModelProperty(value = "知识库 ID", example = "kb-001") private String knowledgeBaseId;
        @ApiModelProperty(value = "文档 ID", example = "doc-001") private String documentId;
        @ApiModelProperty(value = "审核状态", example = "PENDING") private String status;
        @ApiModelProperty(value = "任务视图", example = "available") private String view;
        public KnowledgeReviewTaskQueryVo toQuery() {
            KnowledgeReviewTaskQueryVo query = new KnowledgeReviewTaskQueryVo();
            query.setCurrent(current); query.setPageSize(pageSize); query.setKnowledgeBaseId(knowledgeBaseId); query.setDocumentId(documentId); query.setStatus(status); query.setView(view);
            return query;
        }
    }
    @Data @ApiModel("通过审核请求") public static class ApproveRequest {
        @ApiModelProperty(value = "审核意见", example = "内容符合发布要求") private String comment;
    }
    @Data @ApiModel("拒绝审核请求") public static class RejectRequest {
        @ApiModelProperty(value = "拒绝原因", example = "请补充使用示例") private String comment;
    }
    @Data @ApiModel("编辑审核内容请求") public static class EditContentRequest {
        @ApiModelProperty(value = "Markdown 内容", required = true, example = "# 更新后的文档内容") private String content;
        @ApiModelProperty(value = "内容校验和", required = true, example = "a1b2c3d4") private String expectedChecksum;
    }
}
