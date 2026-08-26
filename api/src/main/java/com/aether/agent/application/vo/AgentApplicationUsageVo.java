package com.aether.agent.application.vo;
import lombok.Data;
/** 业务应用空间的轻量运行运营指标。 */
@Data public class AgentApplicationUsageVo {
    private String applicationId;
    private Long agentRuns;
    private Long workflowRuns;
    private Long totalTokens;
    private Long callbackFailed;
}
