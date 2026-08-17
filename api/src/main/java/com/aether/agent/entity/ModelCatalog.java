package com.aether.agent.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * A selectable model published by a provider. Capabilities are comma-separated enum values.
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName("agent_model_catalog")
public class ModelCatalog extends BaseEntity {
    private String providerId;
    private String name;
    private String capabilities;
    private Integer contextWindow;
    private String endpointOverride;
    private Integer status;
    private String remark;
}
