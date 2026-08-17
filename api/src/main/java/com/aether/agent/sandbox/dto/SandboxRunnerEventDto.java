package com.aether.agent.sandbox.dto;

import lombok.Data;

/**
 * 表示SandboxRunner事件DTO。
 */
@Data
public class SandboxRunnerEventDto {
    private Long sequence;
    private String eventType;
    private Integer progress;
    private String summary;
}
