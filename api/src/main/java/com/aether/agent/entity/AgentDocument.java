package com.aether.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 文档（V0.7预留）
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("agent_document")
@ApiModel(value = "AgentDocument对象", description = "文档")
public class AgentDocument extends BaseEntity {

    @ApiModelProperty(value = "关联知识库ID")
    private String knowledgeBaseId;

    @ApiModelProperty(value = "文档标题")
    private String title;

    @ApiModelProperty(value = "文档内容（纯文本或Markdown）")
    private String content;

    @ApiModelProperty(value = "来源URL（可选）")
    private String sourceUrl;

    @ApiModelProperty(value = "分块数（预留）")
    private Integer chunkCount;

    @ApiModelProperty(value = "状态：0-未处理，1-处理中，2-已完成")
    private Integer status;
}
