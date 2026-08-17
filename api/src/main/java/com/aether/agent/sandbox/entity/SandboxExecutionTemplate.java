package com.aether.agent.sandbox.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表示SandboxExecutionTemplate。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sandbox_execution_template")
public class SandboxExecutionTemplate extends BaseEntity {
    private String code;
    private String name;
    private String description;
    private Boolean enabled;
    private String riskLevel;
    private String currentVersionId;
}
