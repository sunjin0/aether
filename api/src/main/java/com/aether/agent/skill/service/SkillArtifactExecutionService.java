package com.aether.agent.skill.service;

import com.aether.agent.skill.dto.ArtifactGenerationRequestDto;
import com.aether.agent.skill.vo.ArtifactGenerationVo;
import com.aether.agent.skill.vo.SandboxExecutionTaskVo;

public interface SkillArtifactExecutionService {
    ArtifactGenerationVo request(String delegatedToken, ArtifactGenerationRequestDto request);
    SandboxExecutionTaskVo claimNext(String runnerToken);
    void complete(String runnerToken, String executionToken, String executionId, String fileName, String contentType, byte[] content, String sha256, String logSummary, boolean finalArtifact);
    void fail(String runnerToken, String executionToken, String executionId, String reason, String logSummary);
    boolean heartbeat(String runnerToken, String executionToken, String executionId, String logSummary);
    boolean cancelRequested(String runnerToken, String executionToken, String executionId);
    /** Attach artifacts that completed before the Deep run created its final assistant message. */
    void attachPendingArtifacts(String runId, String messageId);
}
