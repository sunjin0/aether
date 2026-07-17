package com.aether.knowledge.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 文档版本分块展示对象。
 * 不返回 embedding，避免向前端暴露大体积向量数据。
 */
@Data
public class KnowledgeDocumentChunkVo {
    @ApiModelProperty(value = "分块 ID")
    private String id;
    /** 分块序号，从 0 开始；对应数据库字段 chunk_index。 */
    private Integer chunkNo;
    /** 分块正文。 */
    private String content;
    /** 估算 Token 数。 */
    private Integer tokenCount;
    /** 创建时间，Unix 毫秒时间戳。 */
    private Long createdAt;
}
