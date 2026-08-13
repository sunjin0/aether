package com.aether.agent.sandbox.vo;

import lombok.Data;

@Data
public class SandboxExecutionResourceUsageVo {
    private Long wallMillis, cpuMillis, maxRssBytes, outputBytes, reportedAt;
    private Integer exitCode;
}
