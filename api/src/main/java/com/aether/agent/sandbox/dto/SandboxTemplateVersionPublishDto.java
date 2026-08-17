package com.aether.agent.sandbox.dto;

import lombok.Data;

/**
 * Administrator request to publish a new immutable template policy version.
 */
@Data
public class SandboxTemplateVersionPublishDto {
    private String configSnapshot;
    private String policyVersion;
    private String riskLevel;
}
