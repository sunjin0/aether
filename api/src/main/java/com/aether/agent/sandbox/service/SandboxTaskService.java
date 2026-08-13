package com.aether.agent.sandbox.service;

import com.aether.agent.sandbox.dto.SandboxRunnerEventDto;
import com.aether.agent.sandbox.dto.SandboxTaskCreateDto;
import com.aether.agent.sandbox.dto.SandboxAuditQueryDto;
import com.aether.agent.sandbox.dto.SandboxRunnerUsageDto;
import com.aether.agent.sandbox.dto.SandboxTemplateVersionPublishDto;
import com.aether.agent.sandbox.entity.SandboxExecutionTemplate;
import com.aether.agent.sandbox.entity.SandboxExecutionTemplateVersion;
import com.aether.agent.sandbox.vo.SandboxRunnerTaskVo;
import com.aether.agent.sandbox.vo.SandboxTaskVo;
import com.aether.agent.sandbox.vo.SandboxMetricsVo;
import java.util.List;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface SandboxTaskService {
    SandboxTaskVo create(String userId, SandboxTaskCreateDto request, boolean autoApprove);
    SandboxTaskVo detail(String taskId, String userId, boolean admin);
    SandboxTaskVo byRun(String runId, String userId, boolean admin);
    List<SandboxTaskVo.SandboxEventVo> events(String taskId, String userId, boolean admin);
    void approve(String taskId, String userId, String reason);
    void reject(String taskId, String userId, String reason);
    void cancel(String taskId, String userId, String reason);
    SandboxTaskVo retry(String taskId, String userId);
    SandboxRunnerTaskVo claim(String runnerId);
    RunnerInputArtifact downloadInput(String taskId, String inputId, String token, String runnerId);
    void reportUsage(String taskId, String token, String runnerId, SandboxRunnerUsageDto usage);
    boolean heartbeat(String taskId, String token, String runnerId, Integer progress, String summary);
    boolean cancelRequested(String taskId, String token, String runnerId);
    void runnerEvent(String taskId, String token, String runnerId, SandboxRunnerEventDto event);
    void succeed(String taskId, String token, String runnerId, String summary);
    void fail(String taskId, String token, String runnerId, String code, String reason, String summary);
    void completeArtifact(String taskId, String token, String runnerId, String fileName, String contentType, byte[] content, String sha256, String summary, boolean finalArtifact);
    /** Compatibility bridge for the pre-platform generic artifact runner. */
    void linkLegacyExecution(String taskId, String legacyExecutionId);
    /** Binds a compatibility task to the final assistant message once the run completes. */
    void linkRunMessage(String runId, String messageId);
    /** True only when the compatibility execution is allowed to be claimed. */
    boolean legacyReadyForClaim(String legacyExecutionId);
    /** Records the compatibility runner's claim and starts the frozen task. */
    void legacyExecutionStarted(String legacyExecutionId, String runnerId);
    void completeLegacyExecution(String legacyExecutionId, boolean success, String reason, String summary);
    boolean legacyHeartbeat(String legacyExecutionId, String summary);
    boolean legacyCancelRequested(String legacyExecutionId);
    List<SandboxExecutionTemplate> templates();
    void setTemplateEnabled(String templateId, boolean enabled);
    SandboxExecutionTemplateVersion publishTemplateVersion(String templateId, String administratorUserId, SandboxTemplateVersionPublishDto request);
    List<SandboxExecutionTemplateVersion> versions(String templateId);
    void recoverExpiredTasks();
    void purgeExpiredRetentionData();
    Page<SandboxTaskVo> audit(SandboxAuditQueryDto query);
    SandboxMetricsVo metrics();

    class RunnerInputArtifact {
        private final String fileName, contentType, sha256;
        private final byte[] content;
        public RunnerInputArtifact(String fileName, String contentType, String sha256, byte[] content) { this.fileName = fileName; this.contentType = contentType; this.sha256 = sha256; this.content = content; }
        public String getFileName() { return fileName; }
        public String getContentType() { return contentType; }
        public String getSha256() { return sha256; }
        public byte[] getContent() { return content; }
    }
}
