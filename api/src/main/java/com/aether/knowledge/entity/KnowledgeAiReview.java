package com.aether.knowledge.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_ai_review")
public class KnowledgeAiReview extends BaseEntity {
    /** 所属知识库 ID。 */
    private String knowledgeBaseId;
    /** 被 AI 审查的文档 ID。 */
    private String documentId;
    /** 被 AI 审查的文档版本 ID。 */
    private String documentVersionId;
    /** 发起 AI 审查时文档内容的 SHA-256 摘要，用于判断审查结果是否过期。 */
    private String sourceChecksum;
    /** 发起 AI 审查时的文档内容不可变快照。 */
    private String sourceContent;
    /** 执行审查的模型供应商 ID。 */
    private String modelProviderId;
    /** 执行审查的模型名称。 */
    private String model;
    /** 审查提示词版本，用于追踪 AI 审查规则变化。 */
    private String promptVersion;
    /** AI 审查状态：pending-待执行，running-执行中，success-成功，failed-失败，stale-内容已变化导致结果过期。 */
    private String status;
    /** AI 审查评分，范围 0-100。 */
    private Integer score;
    /** AI 审查总结。 */
    private String summary;
    /** AI 返回的问题列表 JSON 快照。 */
    private String issues;
    /** 审查统计 JSON，例如 promptTokens、completionTokens、truncated。 */
    private String statistics;
    /** AI 审查失败原因。 */
    private String errorMessage;
    /** AI 审查开始时间，Unix 毫秒时间戳。 */
    private Long startedAt;
    /** AI 审查结束时间，Unix 毫秒时间戳。 */
    private Long finishedAt;
}
