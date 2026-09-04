package com.aether.permission.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 组织内的正式部门节点。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_department")
public class Department extends BaseEntity {
    private String organizationId;
    private String parentId;
    private String code;
    private String name;
    private String managerUserId;
    private String path;
    private Integer level;
}
