package com.aether.organization.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper = true) @TableName("sys_invitation")
public class Invitation extends BaseEntity {
    private String organizationId;
    private String teamId;
    private String email;
    private String roleCode;
    private String tokenHash;
    private Long expiresAt;
    private String status;
    private String inviterId;
    @TableField(exist = false)
    private String token;
}
