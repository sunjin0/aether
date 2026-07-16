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

    @ApiModelProperty(value = "分块数")
    private Integer chunkCount;

    @ApiModelProperty(value = "状态：0-未处理，1-处理中，2-已完成")
    private Integer status;

    private Long current;
    private Long pageSize;
}
