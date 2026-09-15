package com.aether.workflow.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** 提供给 Agent 的脱敏工作流观察结果。 */
@Data
public class AgentWorkflowInvocationObservation {
    private String invocationId;
    private String instanceId;
    private String workflowId;
    private String workflowVersionId;
    private String status;
    private Long stateVersion;
    private String currentNodeId;
    private String currentNodeType;
    private String currentNodeName;
    private String resultCode;
    private Map<String, Object> output;
    private Map<String, Object> nextAction;
    private List<Map<String, Object>> recentEvents;
}
