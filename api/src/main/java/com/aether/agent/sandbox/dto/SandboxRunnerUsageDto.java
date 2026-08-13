package com.aether.agent.sandbox.dto;

import lombok.Data;

@Data
public class SandboxRunnerUsageDto {
    private Long wallMillis, cpuMillis, maxRssBytes, outputBytes;
    private Integer exitCode;
}
