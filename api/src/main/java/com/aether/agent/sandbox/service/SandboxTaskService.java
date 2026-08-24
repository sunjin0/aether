package com.aether.agent.sandbox.service;

import com.aether.agent.sandbox.dto.*;
import com.aether.agent.sandbox.entity.SandboxExecutionTemplate;
import com.aether.agent.sandbox.entity.SandboxExecutionTemplateVersion;
import com.aether.agent.sandbox.vo.SandboxMetricsVo;
import com.aether.agent.sandbox.vo.SandboxRunnerTaskVo;
import com.aether.agent.sandbox.vo.SandboxTaskVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/**
 * 管理沙箱执行任务的创建、审批、Runner 调度、状态回传、审计和模板配置。
 */
public interface SandboxTaskService {
    /**
     * 创建沙箱执行任务并直接进入待调度状态。工具调用的风险识别与用户确认由 MCP 调用链负责。
     */
    SandboxTaskVo create(String userId, SandboxTaskCreateDto request);

    /**
     * 按任务编号查询沙箱任务详情，并按用户归属或管理员权限校验访问。
     */
    SandboxTaskVo detail(String taskId, String userId, boolean admin);

    /**
     * 按智能体运行编号查询关联的沙箱任务，并校验访问权限。
     */
    SandboxTaskVo byRun(String runId, String userId, boolean admin);

    /**
     * 查询任务执行事件列表，并校验访问权限。
     */
    List<SandboxTaskVo.SandboxEventVo> events(String taskId, String userId, boolean admin);

    /**
     * 审批通过待审批任务，使其可由 Runner 领取执行。
     */
    void approve(String taskId, String userId, String reason);

    /**
     * 拒绝待审批任务并记录审批原因。
     */
    void reject(String taskId, String userId, String reason);

    /**
     * 请求取消尚未完成的沙箱任务并记录取消原因。
     */
    void cancel(String taskId, String userId, String reason);

    /**
     * 基于原任务配置创建可再次执行的沙箱任务。
     */
    SandboxTaskVo retry(String taskId, String userId);

    /**
     * 为指定 Runner 领取一项可执行的沙箱任务；无可领取任务时返回空。
     */
    SandboxRunnerTaskVo claim(String runnerId);

    /**
     * 校验 Runner 令牌和任务归属后，下载指定任务输入文件。
     */
    RunnerInputArtifact downloadInput(String taskId, String inputId, String token, String runnerId);

    /**
     * 接收 Runner 上报的任务资源用量。
     */
    void reportUsage(String taskId, String token, String runnerId, SandboxRunnerUsageDto usage);

    /**
     * 接收 Runner 心跳、执行进度和摘要，并返回任务是否已被请求取消。
     */
    boolean heartbeat(String taskId, String token, String runnerId, Integer progress, String summary);

    /**
     * 查询 Runner 当前执行的任务是否已收到取消请求。
     */
    boolean cancelRequested(String taskId, String token, String runnerId);

    /**
     * 记录 Runner 上报的任务执行事件。
     */
    void runnerEvent(String taskId, String token, String runnerId, SandboxRunnerEventDto event);

    /**
     * 将 Runner 已完成的任务标记为成功并保存执行摘要。
     */
    void succeed(String taskId, String token, String runnerId, String summary);

    /**
     * 将 Runner 已完成的任务标记为失败，并保存错误码、失败原因和执行摘要。
     */
    void fail(String taskId, String token, String runnerId, String code, String reason, String summary);

    /**
     * 接收 Runner 产出的文件并关联到任务；{@code finalArtifact} 为 true 时标记为最终产物。
     */
    void completeArtifact(String taskId, String token, String runnerId, String fileName, String contentType, byte[] content, String sha256, String summary, boolean finalArtifact);

    /**
     * Compatibility bridge for the pre-platform generic artifact runner.
     */
    void linkLegacyExecution(String taskId, String legacyExecutionId);

    /**
     * Binds a compatibility task to the final assistant message once the run completes.
     */
    void linkRunMessage(String runId, String messageId);

    /**
     * True only when the compatibility execution is allowed to be claimed.
     */
    boolean legacyReadyForClaim(String legacyExecutionId);

    /**
     * Records the compatibility runner's claim and starts the frozen task.
     */
    void legacyExecutionStarted(String legacyExecutionId, String runnerId);

    /**
     * 同步兼容执行器的最终执行结果到关联的沙箱任务。
     */
    void completeLegacyExecution(String legacyExecutionId, boolean success, String reason, String summary);

    /**
     * 更新兼容执行器关联任务的心跳时间和执行摘要。
     */
    boolean legacyHeartbeat(String legacyExecutionId, String summary);

    /**
     * 查询兼容执行器关联任务是否已收到取消请求。
     */
    boolean legacyCancelRequested(String legacyExecutionId);

    /**
     * 查询全部沙箱执行模板。
     */
    List<SandboxExecutionTemplate> templates();

    /**
     * 启用或停用指定沙箱执行模板。
     */
    void setTemplateEnabled(String templateId, boolean enabled);

    /**
     * 以当前模板配置发布新版本，并记录发布管理员。
     */
    SandboxExecutionTemplateVersion publishTemplateVersion(String templateId, String administratorUserId, SandboxTemplateVersionPublishDto request);

    /**
     * 查询指定沙箱执行模板的版本列表。
     */
    List<SandboxExecutionTemplateVersion> versions(String templateId);

    /**
     * 恢复或终止超过领取、运行或回传时限的任务。
     */
    void recoverExpiredTasks();

    /**
     * 清理超过保留期限的任务、事件和关联数据。
     */
    void purgeExpiredRetentionData();

    /**
     * 按审计条件分页查询沙箱任务。
     */
    Page<SandboxTaskVo> audit(SandboxAuditQueryDto query);

    /**
     * 汇总返回沙箱任务的执行、排队和失败指标。
     */
    SandboxMetricsVo metrics();

    /**
     * Runner 下载任务输入文件时使用的文件内容和校验信息。
     */
    class RunnerInputArtifact {
        private final String fileName, contentType, sha256;
        private final byte[] content;

        /**
         * 创建 {@code RunnerInputArtifact} 实例。
         */
        public RunnerInputArtifact(String fileName, String contentType, String sha256, byte[] content) {
            this.fileName = fileName;
            this.contentType = contentType;
            this.sha256 = sha256;
            this.content = content;
        }

        /**
         * 获取文件Name。
         */
        public String getFileName() {
            return fileName;
        }

        /**
         * 获取ContentType。
         */
        public String getContentType() {
            return contentType;
        }

        /**
         * 获取Sha256。
         */
        public String getSha256() {
            return sha256;
        }

        /**
         * 获取Content。
         */
        public byte[] getContent() {
            return content;
        }
    }
}
