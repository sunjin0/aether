package com.aether.knowledge.vo;

import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * 文档 VO（V0.7预留）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KnowledgeDocumentVo extends BaseEntity {


    @ApiModelProperty(value = "关联知识库ID")
    private String knowledgeBaseId;

    @ApiModelProperty(value = "文档标题")
    private String title;

    @ApiModelProperty(value = "文档内容")
    private String content;

    @ApiModelProperty(value = "来源URL")
    private String sourceUrl;

    /** 文档来源：text-文本/Markdown，file-上传文件。 */
    private String sourceType;
    private String originalFileName;
    private String fileExtension;
    private String mimeType;
    private Long fileSize;
    private String fileChecksum;
    private String storageBucket;
    private String storageObjectKey;
    private Integer currentVersionNo;
    /** 索引状态：0-未索引，1-索引中，2-已完成，3-失败。 */
    private Integer indexStatus;
    private String parserType;
    private String indexErrorMessage;
    private Long indexedAt;
    private Long referenceCount;
    private Long lastReferencedAt;


    @ApiModelProperty(value = "分块数")
    private Integer chunkCount;

    @ApiModelProperty(value = "状态：0-未处理，1-处理中，2-已完成")
    private Integer status;

    private Long current;
    private Long pageSize;
}
