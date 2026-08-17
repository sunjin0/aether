package com.aether.agent.skill.service;

import com.aether.agent.skill.dto.ArtifactGenerationRequestDto;
import com.aether.agent.skill.vo.ArtifactGenerationVo;
import com.aether.agent.skill.vo.SandboxExecutionTaskVo;

/**
 * 管理 Skill 产物生成请求与兼容执行器的任务回传。
 */
public interface SkillArtifactExecutionService {
    /**
     * 创建委托令牌授权的文件生成请求。
     */
    ArtifactGenerationVo request(String delegatedToken, ArtifactGenerationRequestDto request);

    /**
     * 认领下一个待处理任务。
     */
    SandboxExecutionTaskVo claimNext(String runnerToken);

    /**
     * 接收兼容执行器生成的文件，并保存执行结果。
     */
    void complete(String runnerToken, String executionToken, String executionId, String fileName, String contentType, byte[] content, String sha256, String logSummary, boolean finalArtifact);

    /**
     * 记录兼容执行器的失败原因和执行摘要。
     */
    void fail(String runnerToken, String executionToken, String executionId, String reason, String logSummary);

    /**
     * 更新兼容执行器的心跳时间和执行摘要。
     */
    boolean heartbeat(String runnerToken, String executionToken, String executionId, String logSummary);

    /**
     * 查询兼容执行器当前任务是否已收到取消请求。
     */
    boolean cancelRequested(String runnerToken, String executionToken, String executionId);

    /**
     * Attach artifacts that completed before the Deep run created its final assistant message.
     */
    void attachPendingArtifacts(String runId, String messageId);
}
