package com.aether.knowledge.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表示知识库Ai审核Issue。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_ai_review_issue")
public class KnowledgeAiReviewIssue extends BaseEntity {
    /**
     * 所属 AI 审查记录 ID。
     */
    private String aiReviewId;
    /**
     * 问题所属文档版本 ID。
     */
    private String documentVersionId;
    /**
     * AI 输出的问题块 ID；缺省时按 block-{index} 生成。
     */
    private String blockId;
    /**
     * 问题类型：由 AI 输出的 type 字段决定，缺省为 quality。
     */
    private String issueType;
    /**
     * 严重程度：info-提示，warning-警告，critical-严重；无法识别时默认 warning。
     */
    private String severity;
    /**
     * 问题描述。
     */
    private String message;
    /**
     * 原文中命中的片段，最长保留 2000 字符。
     */
    private String originalExcerpt;
    /**
     * AI 建议补丁 JSON；operation 支持 replace、insert_before、insert_after、delete、set_heading。
     */
    private String suggestedPatch;
    /**
     * 问题处理状态：pending-待处理，accepted-已接受待应用，rejected-已拒绝，manually_fixed-已人工修复，ignored-已忽略。
     */
    private String handleStatus;
    /**
     * 处理该问题的管理员 ID。
     */
    private String handledBy;
    /**
     * 问题处理时间，Unix 毫秒时间戳。
     */
    private Long handledAt;
    /**
     * 问题处理备注。
     */
    private String handleComment;
    /**
     * 人工接受的实际替换内容，可能不同于 AI 建议。
     */
    private String appliedContent;
    /**
     * 已接受问题应用到草稿后的文档内容 SHA-256 摘要；为空表示尚未真正写入草稿。
     */
    private String appliedChecksum;
}
