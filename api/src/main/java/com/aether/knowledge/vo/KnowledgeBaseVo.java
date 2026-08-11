package com.aether.knowledge.vo;

import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库 VO（V0.7预留）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KnowledgeBaseVo extends BaseEntity {

    @ApiModelProperty(value = "关联Agent定义ID")
    private String scope;

    private String embeddingProviderId;
    private String embeddingModelId;

    /** 后台管理员归属 ID。 */
    private String ownerAdminId;
    /** 可见性：platform-平台，private-私有，shared-共享。 */
    private String visibility;
    /** 检索配置 JSON；当前只保存，暂不启用混合检索/重排。 */
    private String retrievalConfig;
    private String reviewConfig;
    /** 实际回答引用累计次数。 */
    private Long referenceCount;
    /** 最近实际引用时间，Unix 毫秒时间戳。 */
    private Long lastReferencedAt;

    @ApiModelProperty(value = "知识库名称")
    private String name;

    @ApiModelProperty(value = "描述")
    private String description;

    @ApiModelProperty(value = "索引状态：0-未索引，1-索引中，2-已索引")
    private Integer indexStatus;

    @ApiModelProperty(value = "状态：0-禁用，1-启用")
    private Integer status;

    private Long current;
    private Long pageSize;
}
