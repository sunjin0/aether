package com.aether.agent.vo;
import lombok.Data; import java.util.List;
@Data public class AgentRunPlanVo { private String runId, status, pauseReason, currentStepId; private Integer currentVersion; private Long lastActiveAt; private List<Version> versions; @Data public static class Version { private Integer version; private String reason, summary; private List<Step> steps; } @Data public static class Step { private String id, stepKey, title, status, resultSummary; private Integer sequence, attemptCount; private Long startedAt, completedAt; } }
