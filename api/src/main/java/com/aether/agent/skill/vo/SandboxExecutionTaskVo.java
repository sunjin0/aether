package com.aether.agent.skill.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Frozen task readable only by the platform Runner after it claims the queue item.
 */
@Data
public class SandboxExecutionTaskVo {
    private String executionId;
    private String executionToken;
    private String runId;
    private String skillVersionId;
    private String runtime;
    private String entryResourceId;
    private List<String> outputFormats;
    private Integer timeoutSeconds;
    private Integer maxOutputFiles;
    private Long maxOutputBytes;
    private Map<String, Object> input;
    private List<Resource> resources;

    /**
     * 表示资源。
     */
    @Data
    public static class Resource {
        private String id;
        private String name;
        private String type;
        private String language;
        private String contentSha256;
        private Long size;
    }
}
