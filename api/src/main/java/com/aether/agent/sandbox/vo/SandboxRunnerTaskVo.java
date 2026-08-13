package com.aether.agent.sandbox.vo;

import lombok.Data;
import java.util.Map;
import java.util.List;

/** Fully frozen task returned only after an atomic runner claim. */
@Data
public class SandboxRunnerTaskVo {
    private String taskId, executionToken, templateCode, runtime, executionMode, fixedCommand, imageRef;
    private Integer timeoutSeconds, maxOutputFiles;
    private Integer maxMemoryMb, maxPids, maxTempDiskMb;
    private Double maxCpuCores;
    private Long maxOutputBytes;
    private List<String> outputFormats;
    private List<SandboxRunnerInputArtifactVo> inputArtifacts;
    private Map<String, Object> input;
}
