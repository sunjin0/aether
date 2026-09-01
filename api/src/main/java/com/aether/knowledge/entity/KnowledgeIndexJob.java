package com.aether.knowledge.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表示知识库索引Job。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_index_job")
public class KnowledgeIndexJob extends BaseEntity {
    /** 异步任务创建时冻结的租户边界。 */
    private String tenantId;
    /**
     * 所属知识库 ID。
     */
    private String knowledgeBaseId;
    /**
     * 被索引的文档 ID。
     */
    private String documentId;
    /**
     * 被索引的文档版本 ID。
     */
    private String documentVersionId;
    /**
     * 任务类型：create-新建文本，upload-上传文件，update-编辑，reindex-重建，rollback-回滚，retry-人工重试。
     */
    private String jobType;
    /**
     * 任务状态：pending-待执行，running-执行中，success-成功，failed-重试耗尽失败，cancelled-取消（预留）。
     */
    private String status;
    /**
     * 当前已执行的失败重试次数。
     */
    private Integer retryCount;
    /**
     * 最大自动重试次数，当前默认 3 次。
     */
    private Integer maxRetryCount;
    /**
     * 最后一次异常信息。
     */
    private String errorMessage;
    /**
     * 任务统计 JSON，预留解析页数、分块数、耗时等字段。
     */
    private String statistics;
    /**
     * 开始执行时间，Unix 毫秒时间戳。
     */
    private Long startedAt;
    /**
     * 结束执行时间，Unix 毫秒时间戳。
     */
    private Long finishedAt;
}
