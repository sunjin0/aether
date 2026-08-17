package com.aether.agent.sandbox.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表示SandboxExecutionTemplateVersion。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sandbox_execution_template_version")
public class SandboxExecutionTemplateVersion extends BaseEntity {
    private String templateId;
    private Integer version;
    private Boolean published;
    private String configSnapshot;
    private String policyVersion;
    private String publishedBy;
    private Long publishedAt;
}
