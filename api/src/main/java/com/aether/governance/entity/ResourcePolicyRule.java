package com.aether.governance.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Explicit resource policy rule. Deny rules take precedence over allow rules. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aether_resource_policy_rule")
public class ResourcePolicyRule extends BaseEntity {
    private String subjectType;
    private String subjectId;
    private String resourceType;
    private String resourceId;
    private String action;
    private String effect;
    private String conditionJson;
}
