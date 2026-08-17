package com.aether.knowledge.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表示知识库文档Version。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_document_version")
public class KnowledgeDocumentVersion extends BaseEntity {
    /**
     * 所属 knowledge_document 的 ID。
     */
    private String knowledgeDocumentId;
    /**
     * 文档版本号，从 1 开始递增；回滚会生成一个新的最新版本。
     */
    private Integer versionNo;
    /**
     * 该版本的可检索文本快照。
     */
    private String content;
    /**
     * 解析器产生的原始文本，人工和 AI 均不得覆盖。
     */
    private String originalContent;
    /**
     * 经人工确认的结构化索引文本；为空时索引 content。
     */
    private String structuredContent;
    /**
     * 当前候选正文的 SHA-256，用于并发和审查过期校验。
     */
    private String contentChecksum;
    /**
     * 版本审核状态：DRAFT-草稿，AI_REVIEWING-AI 审查中，AI_REVIEWED-AI 已审查，SUBMITTED-已提交人工审核，APPROVED-已通过，REJECTED-已驳回。
     */
    private String reviewStatus;
    /**
     * 回滚或修订所基于的历史版本 ID。
     */
    private String sourceVersionId;
    /**
     * 提交审核的管理员 ID。
     */
    private String submittedBy;
    /**
     * 提交审核时间，Unix 毫秒时间戳。
     */
    private Long submittedAt;
    /**
     * 审核该版本的管理员 ID。
     */
    private String reviewedBy;
    /**
     * 审核完成时间，Unix 毫秒时间戳。
     */
    private Long reviewedAt;
    /**
     * 审核意见；通过时为通过意见，驳回时为驳回原因。
     */
    private String reviewComment;
    /**
     * 此版本原始文件所在的 MinIO Bucket。
     */
    private String storageBucket;
    /**
     * 此版本原始文件的 MinIO 对象键。
     */
    private String storageObjectKey;
    /**
     * 此版本原始内容的 SHA-256 摘要。
     */
    private String fileChecksum;
    /**
     * 解析器类型：text、pdf、docx。
     */
    private String parserType;
    /**
     * 版本索引状态：0-未索引，1-索引中，2-已完成，3-失败。
     */
    private Integer indexStatus;
    /**
     * 当前版本最后一次索引失败原因。
     */
    private String indexErrorMessage;
    /**
     * 当前版本完成索引的 Unix 毫秒时间戳。
     */
    private Long indexedAt;
    /**
     * 该版本写入的向量分块数量。
     */
    private Integer chunkCount;
}
