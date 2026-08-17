package com.aether.agent.sandbox.dto;

import lombok.Data;

/**
 * 表示SandboxRunnerUsageDTO。
 */
@Data
public class SandboxRunnerUsageDto {
    private Long wallMillis, cpuMillis, maxRssBytes, outputBytes;
    private Integer exitCode;
}
