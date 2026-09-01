package com.aether.tenant.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 企业租户目录；后续业务数据通过 tenantId 关联此稳定身份。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aether_tenant")
public class Tenant extends BaseEntity {
    private String code;
    private String name;
    private Integer status;
}
