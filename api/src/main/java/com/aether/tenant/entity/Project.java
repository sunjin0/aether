package com.aether.tenant.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 工作空间内的项目边界，可承载应用和配额配置。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aether_project")
public class Project extends BaseEntity {
    private String workspaceId;
    private String applicationId;
    private String code;
    private String name;
    private Integer status;
}
