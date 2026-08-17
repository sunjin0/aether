package com.aether.knowledge.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表示知识库审核任务。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_review_task")
public class KnowledgeReviewTask extends BaseEntity {
    /**
     * 所属知识库 ID。
     */
    private String knowledgeBaseId;
    /**
     * 待审核文档 ID。
     */
    private String documentId;
    /**
     * 待审核文档版本 ID。
     */
    private String documentVersionId;
    /**
     * 提交审核的管理员 ID。
     */
    private String submitterId;
    /**
     * 认领或实际审核的管理员 ID。
     */
    private String reviewerId;
    /**
     * 审核任务状态：pending-待认领/待审核，claimed-已认领，approved-已通过，rejected-已驳回，cancelled-已取消（预留）。
     */
    private String status;
    /**
     * 提交审核时文档版本内容的 SHA-256 摘要，用于检测提交后内容是否变化。
     */
    private String sourceChecksum;
    /**
     * 提交审核时填写的说明。
     */
    private String submitComment;
    /**
     * 审核意见；通过时为通过意见，驳回时为驳回原因。
     */
    private String reviewComment;
    /**
     * 提交审核时间，Unix 毫秒时间戳。
     */
    private Long submittedAt;
    /**
     * 审核任务认领时间，Unix 毫秒时间戳。
     */
    private Long claimedAt;
    /**
     * 审核完成时间，Unix 毫秒时间戳。
     */
    private Long reviewedAt;
}
