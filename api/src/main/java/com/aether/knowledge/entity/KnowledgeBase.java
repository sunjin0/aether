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

    @ApiModelProperty(value = "所属业务应用空间")
    private String applicationId;

    /**
     * 知识库范围：PLATFORM-平台公共库，AGENT-Agent 专属库。
     */
    @ApiModelProperty(value = "知识库范围：PLATFORM-平台公共库，AGENT-Agent专属库")
    private String scope;

    /**
     * Embedding 模型供应商 ID，由模型供应商下拉接口提供。
     */
    private String embeddingProviderId;
    private String embeddingModelId;

    /**
     * 知识库归属的后台管理员 ID。
     */
    private String ownerAdminId;
    /**
     * 可见性：platform-平台可见，private-仅所有者可见，shared-成员共享。
     */
    private String visibility;
    /**
     * 检索配置 JSON，例如 topK、minSimilarity、hybridEnabled、vectorWeight、minLexicalScore、重排参数。
     */
    private String retrievalConfig;
    /**
     * 文档审批策略 JSON，例如 requireDifferentApprover。
     */
    private String reviewConfig;
    /**
     * 最终回答实际引用本知识库的累计次数，不统计仅召回的候选片段。
     */
    private Long referenceCount;
    /**
     * 最近一次实际引用时间，Unix 毫秒时间戳。
     */
    private Long lastReferencedAt;

    /**
     * 知识库名称。
     */
    @ApiModelProperty(value = "知识库名称")
    private String name;

    /**
     * 知识库描述。
     */
    @ApiModelProperty(value = "描述")
    private String description;

    /**
     * 索引状态：0-未索引，1-索引中，2-已完成，3-索引失败。
     */
    @ApiModelProperty(value = "索引状态：0-未索引，1-索引中，2-已完成，3-索引失败")
    private Integer indexStatus;

    /**
     * 知识库状态：0-禁用，1-启用。
     */
    @ApiModelProperty(value = "状态：0-禁用，1-启用")
    private Integer status;
}
