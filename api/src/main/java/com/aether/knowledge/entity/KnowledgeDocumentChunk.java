package com.aether.knowledge.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
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
}
