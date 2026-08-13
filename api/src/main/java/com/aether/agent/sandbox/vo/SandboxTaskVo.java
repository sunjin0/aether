package com.aether.agent.sandbox.vo;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class SandboxTaskVo {
    private String id, legacyExecutionId, templateCode, status, riskLevel, runId, messageId, failureCode, failureReason, logSummary;
    private Boolean approvalRequired, cancelRequested;
    private Long createdAt, startedAt, completedAt, expiresAt;
    private List<SandboxEventVo> events;
    private List<SandboxApprovalVo> approvals;
    private SandboxExecutionResourceUsageVo resourceUsage;
    private Map<String, Object> approvalSummary;
    @Data public static class SandboxEventVo { private Long sequence, occurredAt; private String eventType, status, summary, subjectSha256; private Integer progress; }
}
