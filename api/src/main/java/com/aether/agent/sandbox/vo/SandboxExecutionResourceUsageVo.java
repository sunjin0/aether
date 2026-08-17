package com.aether.agent.sandbox.vo;

import lombok.Data;

/**
 * 表示SandboxExecution资源UsageVO。
 */
@Data
public class SandboxExecutionResourceUsageVo {
    private Long wallMillis, cpuMillis, maxRssBytes, outputBytes, reportedAt;
    private Integer exitCode;
}
