package com.aether.permission.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户与部门的归属关系，不直接承载角色权限。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_department_member")
public class DepartmentMember extends BaseEntity {
    private String organizationId;
    private String departmentId;
    private String userId;
    private Boolean primaryDepartment;
    private String positionName;
    /** 部门内身份，仅用于组织架构展示，不参与权限计算。 */
    private String identityCode;
}
