package com.aether.tenant.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 租户内的工作空间边界。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aether_workspace")
public class Workspace extends BaseEntity {
    private String tenantId;
    private String code;
    private String name;
    private Integer status;
}
