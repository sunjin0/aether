package com.aether.sys.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** External OIDC subject binding; identity matching is never inferred from email alone. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_oidc_identity_binding")
public class OidcIdentityBinding extends BaseEntity {
    private String tenantId;
    private String issuer;
    private String subject;
    private String userId;
    private String emailSnapshot;
    private Long lastLoginAt;
}
