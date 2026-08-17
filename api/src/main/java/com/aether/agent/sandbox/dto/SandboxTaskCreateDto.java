package com.aether.agent.sandbox.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * User-visible request; execution boundaries are always sourced from a template.
 */
@Data
public class SandboxTaskCreateDto {
    private String templateCode;
    private String agentDefinitionId;
    private String runId;
    private String messageId;
    /**
     * Accepted only when the published template explicitly declares a script slot.
     */
    private String script;
    private String scriptLanguage;
    /**
     * IDs of requester-owned artifact-library files; callers never provide object keys or filesystem paths.
     */
    private List<String> inputArtifactIds;
    private Map<String, Object> input;
}
