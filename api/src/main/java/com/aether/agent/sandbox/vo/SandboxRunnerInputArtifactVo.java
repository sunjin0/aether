package com.aether.agent.sandbox.vo;

import lombok.Data;

/** Immutable input metadata; the Runner downloads bytes with its task lease. */
@Data
public class SandboxRunnerInputArtifactVo {
    private String id;
    private String fileName;
    private String contentType;
    private String sha256;
    private Long size;
}
