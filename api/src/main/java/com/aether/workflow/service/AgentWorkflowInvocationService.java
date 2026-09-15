package com.aether.workflow.service;

import com.aether.workflow.entity.AgentWorkflowInvocation;
import com.aether.workflow.vo.AgentWorkflowInvocationObservation;

import java.util.Map;

/** Agent 调用工作流的受控领域入口。 */
public interface AgentWorkflowInvocationService {
    AgentWorkflowInvocationResult start(String capabilityId, String operationKey, Map<String, Object> input,
                                        String agentDefinitionId, String agentRunId, String agentTaskId,
                                        String toolCallId, String userId, String applicationId);

    AgentWorkflowInvocationObservation observe(String invocationId, String userId, String agentDefinitionId);

    default AgentWorkflowInvocationResult stop(String invocationId, String reason, String userId, String agentDefinitionId) {
        return stop(invocationId, reason, null, userId, agentDefinitionId);
    }

    AgentWorkflowInvocationResult stop(String invocationId, String reason, Long expectedStateVersion,
                                       String userId, String agentDefinitionId);

    AgentWorkflowInvocationResult signalEvent(String invocationId, String eventType, String eventId,
                                              String correlationKey, Map<String, Object> data,
                                              Long expectedStateVersion, String userId, String agentDefinitionId);

    AgentWorkflowInvocationResult retryNode(String invocationId, String nodeId, Long expectedStateVersion,
                                             String userId, String agentDefinitionId);

    /** 结构化输入仅允许工作流节点明确声明为 Agent 可写。 */
    AgentWorkflowInvocationResult provideAgentInput(String invocationId, Map<String, Object> input,
                                                    Long expectedStateVersion, String userId, String agentDefinitionId);

    /**
     * 提交工作流 MCP 授权节点的决定。该动作仅适用于当前调用者拥有、且正等待
     * MCP 工具授权的实例；决定值由领域服务限制为 once、allow_10m 或 reject。
     */
    AgentWorkflowInvocationResult resolveMcpApproval(String invocationId, String decision,
                                                     Long expectedStateVersion, String userId,
                                                     String agentDefinitionId);

    class AgentWorkflowInvocationResult {
        private String invocationId;
        private String instanceId;
        private String status;
        private String resultCode;
        private String message;
        private String workflowVersionId;
        private Map<String, Object> nextAction;

        public String getInvocationId() { return invocationId; }
        public void setInvocationId(String value) { invocationId = value; }
        public String getInstanceId() { return instanceId; }
        public void setInstanceId(String value) { instanceId = value; }
        public String getStatus() { return status; }
        public void setStatus(String value) { status = value; }
        public String getResultCode() { return resultCode; }
        public void setResultCode(String value) { resultCode = value; }
        public String getMessage() { return message; }
        public void setMessage(String value) { message = value; }
        public String getWorkflowVersionId() { return workflowVersionId; }
        public void setWorkflowVersionId(String value) { workflowVersionId = value; }
        public Map<String, Object> getNextAction() { return nextAction; }
        public void setNextAction(Map<String, Object> value) { nextAction = value; }
    }
}
