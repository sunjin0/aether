package com.aether.knowledge.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * Knowledge-base document chunk backed by pgvector.
 *
 * This is persistence infrastructure only. Embedding generation and retrieval
 * are intentionally implemented in a later knowledge-base feature.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("knowledge_document_chunk")
@ApiModel(value = "KnowledgeDocumentChunk", description = "知识库文档分块")
public class KnowledgeDocumentChunk extends BaseEntity {

    @ApiModelProperty(value = "关联知识库ID")
    private String knowledgeBaseId;

    @ApiModelProperty(value = "关联文档ID")
    private String documentId;

    /** 生成该分块的文档版本 ID；只检索当前成功版本的分块。 */
    private String documentVersionId;
    /** 原始文件页码；纯文本/Markdown 文档为空。 */
    private Integer pageNo;
    /** Markdown 标题层级或文档章节路径。 */
    private String sectionPath;
    /** 分块内容 SHA-256 摘要，用于判重和增量索引。 */
    private String contentHash;
    /** 分块扩展元数据 JSON，例如页码、章节、来源位置。 */
    private String metadata;
    /** 最终回答实际引用该分块的累计次数。 */
    private Long referenceCount;
    /** 最近一次实际引用时间，Unix 毫秒时间戳。 */
    private Long lastReferencedAt;

    @ApiModelProperty(value = "文档内分块序号")
    private Integer chunkIndex;

    @ApiModelProperty(value = "分块文本")
    private String content;

    @ApiModelProperty(value = "分块Token数")
    private Integer tokenCount;

    /**
     * pgvector textual representation. The current migration only defines the
     * persistence field; it does not expose vector CRUD or retrieval APIs.
     */
    @ApiModelProperty(value = "1536维 pgvector 文本表示")
    private String embedding;

    /** Cosine similarity returned by retrieval queries; not persisted as a column. */
    @TableField(exist = false)
    private Double similarity;
}
