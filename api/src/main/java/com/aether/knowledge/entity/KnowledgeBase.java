package com.aether.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 知识库（V0.7预留）
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("knowledge_base")
@ApiModel(value = "KnowledgeBase对象", description = "知识库")
public class KnowledgeBase extends BaseEntity {

    @ApiModelProperty(value = "关联Agent定义ID")
    private String scope;

    private String embeddingProviderId;

    @ApiModelProperty(value = "知识库名称")
    private String name;

    @ApiModelProperty(value = "描述")
    private String description;

    @ApiModelProperty(value = "索引状态：0-未索引，1-索引中，2-已索引")
    private Integer indexStatus;

    @ApiModelProperty(value = "状态：0-禁用，1-启用")
    private Integer status;
}
