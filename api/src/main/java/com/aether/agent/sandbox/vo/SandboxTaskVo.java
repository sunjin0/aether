package com.aether.agent.sandbox.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 表示Sandbox任务VO。
 */
@Data
public class SandboxTaskVo {
    private String id, legacyExecutionId, templateCode, status, riskLevel, runId, messageId, failureCode, failureReason, logSummary;
    private Boolean approvalRequired, cancelRequested;
    private Long createdAt, startedAt, completedAt, expiresAt;
    private List<SandboxEventVo> events;
    private List<SandboxApprovalVo> approvals;
    private SandboxExecutionResourceUsageVo resourceUsage;
    private Map<String, Object> approvalSummary;

    /**
     * 表示Sandbox事件VO。
     */
    @Data
    public static class SandboxEventVo {
        private Long sequence, occurredAt;
        private String eventType, status, summary, subjectSha256;
        private Integer progress;
    }
}
