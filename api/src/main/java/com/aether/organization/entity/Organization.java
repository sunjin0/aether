package com.aether.organization.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper = true) @TableName("sys_organization")
public class Organization extends BaseEntity {
    private String code;
    private String name;
    private String ownerId;
}
