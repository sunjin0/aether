package com.aether.organization.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper = true) @TableName("sys_team_member")
public class TeamMember extends BaseEntity {
    private String organizationId;
    private String teamId;
    private String userId;
    private String roleCode;
}
