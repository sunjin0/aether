package com.aether.agent.sandbox.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Runner-reported, bounded execution usage. Missing metrics are intentionally null rather than guessed.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sandbox_execution_resource_usage")
public class SandboxExecutionResourceUsage extends BaseEntity {
    private String taskId;
    private Long wallMillis, cpuMillis, maxRssBytes, outputBytes;
    private Integer exitCode;
    private Long reportedAt;
}
