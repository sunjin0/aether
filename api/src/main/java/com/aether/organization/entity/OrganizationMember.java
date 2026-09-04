package com.aether.organization.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper = true) @TableName("sys_organization_member")
public class OrganizationMember extends BaseEntity {
    private String organizationId;
    private String userId;
    private String roleCode;
    private String source;
}
