package com.aether.workflow.runtime;

import com.aether.workflow.entity.AgentWorkflowCapability;
import com.aether.workflow.entity.AgentWorkflowExternalInvocation;
import com.aether.workflow.entity.AgentWorkflowNodeInstance;
import com.aether.workflow.service.AgentWorkflowExternalInvocationService;
import com.aether.workflow.vo.AgentWorkflowInstanceVo;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 表驱动锁住 nextAction 的全部等待/终止分支。
 *
 * <p>这些断言写于逻辑从 {@code AgentWorkflowInvocationServiceImpl} 抽出之前，
 * 用来证明「提取是搬运而不是重写」—— 任何一条断言变化都意味着 observe 的行为漂了。
 */
class AgentWorkflowNextActionResolverTest {
    @Test
    void mcpToolApprovalNodeAsksForAnAuthorizationDecision() {
        AgentWorkflowNextActionResolver resolver = resolver();
        AgentWorkflowInstanceVo snapshot = snapshot("WAITING_USER",
                node("approve_1", "{\"approvalType\":\"mcp_tool_approval\",\"toolName\":\"send_email\",\"question\":\"允许发送吗\"}"));

        Map<String, Object> action = resolver.resolve(capability("RESOLVE_MCP_APPROVAL"), snapshot);

        assertEquals("RESOLVE_MCP_APPROVAL", action.get("type"));
        assertEquals(Arrays.asList("invocationId", "expectedStateVersion", "decision"), action.get("required"));
        assertEquals(Boolean.TRUE, action.get("authorizationRequired"));
        assertEquals(Arrays.asList("once", "allow_10m", "reject"), action.get("decisions"));
        assertEquals("approve_1", action.get("nodeId"));
        assertEquals("send_email", action.get("toolName"));
        assertEquals("允许发送吗", action.get("question"));
    }

    @Test
    void mcpApprovalIsNotOfferedWhenTheCapabilityCannotResolveIt() {
        AgentWorkflowNextActionResolver resolver = resolver();
        AgentWorkflowInstanceVo snapshot = snapshot("WAITING_USER",
                node("approve_1", "{\"approvalType\":\"mcp_tool_approval\",\"toolName\":\"send_email\"}"));

        Map<String, Object> action = resolver.resolve(capability("OBSERVE"), snapshot);

        assertEquals("WAITING_HUMAN", action.get("type"));
    }

    @Test
    void agentInputNodeExposesTheNodeSchema() {
        AgentWorkflowNextActionResolver resolver = resolver();
        AgentWorkflowInstanceVo snapshot = snapshot("WAITING_USER", node("fill_1", null));
        snapshot.setVersionNodes("[{\"id\":\"fill_1\",\"name\":\"补全工单\",\"agentInputAllowed\":true,"
                + "\"agentInputSchema\":{\"type\":\"object\",\"properties\":{\"assignee\":{\"type\":\"string\"}}}}]");

        Map<String, Object> action = resolver.resolve(capability("PROVIDE_AGENT_INPUT"), snapshot);

        assertEquals("PROVIDE_AGENT_INPUT", action.get("type"));
        assertEquals(Arrays.asList("invocationId", "expectedStateVersion", "input"), action.get("required"));
        assertEquals("fill_1", action.get("nodeId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) action.get("schema");
        assertEquals("object", schema.get("type"));
        com.alibaba.fastjson2.JSONObject properties = com.alibaba.fastjson2.JSONObject.parseObject(
                com.alibaba.fastjson2.JSON.toJSONString(schema.get("properties")));
        assertEquals("string", properties.getJSONObject("assignee").getString("type"));
    }

    @Test
    void approvalModeNeverOffersAgentInputEvenWhenExplicitlyAllowed() {
        AgentWorkflowNextActionResolver resolver = resolver();
        AgentWorkflowInstanceVo snapshot = snapshot("WAITING_USER", node("approve_1", null));
        snapshot.setVersionNodes("[{\"id\":\"approve_1\",\"mode\":\"approval\",\"agentInputAllowed\":true,"
                + "\"agentInputPolicy\":\"AGENT_INPUT_ALLOWED\"}]");

        Map<String, Object> action = resolver.resolve(capability("PROVIDE_AGENT_INPUT"), snapshot);

        assertEquals("WAITING_HUMAN", action.get("type"));
    }

    @Test
    void agentInputPolicyAloneIsEnoughToOfferInput() {
        AgentWorkflowNextActionResolver resolver = resolver();
        AgentWorkflowInstanceVo snapshot = snapshot("WAITING_USER", node("fill_1", null));
        snapshot.setVersionNodes("[{\"id\":\"fill_1\",\"inputPolicy\":\"AGENT_INPUT_ALLOWED\"}]");

        Map<String, Object> action = resolver.resolve(capability("PROVIDE_AGENT_INPUT"), snapshot);

        assertEquals("PROVIDE_AGENT_INPUT", action.get("type"));
    }

    @Test
    void userWaitWithNoGrantedActionFallsBackToTheHuman() {
        AgentWorkflowNextActionResolver resolver = resolver();
        AgentWorkflowInstanceVo snapshot = snapshot("WAITING_USER", node("fill_1", null));

        Map<String, Object> action = resolver.resolve(capability("OBSERVE"), snapshot);

        assertEquals(Collections.singletonMap("type", "WAITING_HUMAN"), action);
    }

    @Test
    void aRunningToolNodeWarnsAgainstCallingThatToolDirectly() {
        AgentWorkflowNextActionResolver resolver = resolver();
        AgentWorkflowNodeInstance node = node("tool_1", null);
        node.setNodeType("tool");
        AgentWorkflowInstanceVo snapshot = snapshot("RUNNING", node);
        snapshot.setVersionNodes("[{\"id\":\"tool_1\",\"type\":\"tool\","
                + "\"resourceId\":\"2081210365587558401\",\"toolName\":\"process_document\"}]");

        Map<String, Object> action = resolver.resolve(capability("OBSERVE"), snapshot);

        assertEquals("OBSERVE", action.get("type"));
        // 直接调这个工具会另起一套审批门、绕过工作流的幂等包裹，提示必须点名到具体工具。
        String note = String.valueOf(action.get("note"));
        assertTrue(note.contains("process_document"), note);
        assertTrue(note.contains("workflow_observe"), note);
    }

    @Test
    void aRunningNonToolNodeCarriesNoSuchWarning() {
        AgentWorkflowNextActionResolver resolver = resolver();
        AgentWorkflowInstanceVo snapshot = snapshot("RUNNING", node("fill_1", null));

        Map<String, Object> action = resolver.resolve(capability("OBSERVE"), snapshot);

        assertEquals("OBSERVE", action.get("type"));
        assertFalse(action.containsKey("note"));
    }

    @Test
    void resultCodesMatchTheObserveContract() {
        // 任务清单与 observe 共用这一份映射；两份实现漂移，同一个调用就会出现两个结论。
        assertEquals("WORKFLOW_COMPLETED", AgentWorkflowNextActionResolver.resultCode("COMPLETED"));
        assertEquals("WORKFLOW_FAILED", AgentWorkflowNextActionResolver.resultCode("FAILED"));
        assertEquals("WORKFLOW_TERMINATED", AgentWorkflowNextActionResolver.resultCode("TERMINATED"));
        assertEquals("WORKFLOW_TIMED_OUT", AgentWorkflowNextActionResolver.resultCode("TIMED_OUT"));
        assertEquals("WORKFLOW_WAITING_HUMAN", AgentWorkflowNextActionResolver.resultCode("WAITING_USER"));
        assertEquals("WORKFLOW_RUNNING", AgentWorkflowNextActionResolver.resultCode("RUNNING"));
    }

    @Test
    void eventWaitPublishesTheExpectedEventContract() {
        AgentWorkflowNextActionResolver resolver = resolver();
        AgentWorkflowInstanceVo snapshot = snapshot("WAITING_EVENT",
                node("wait_1", "{\"eventType\":\"ticket.completed\",\"correlationKey\":\"T-1\"}"));

        Map<String, Object> action = resolver.resolve(capability("SIGNAL_EVENT"), snapshot);

        assertEquals("SIGNAL_EVENT", action.get("type"));
        assertEquals(Arrays.asList("invocationId", "expectedStateVersion", "eventType", "eventId"), action.get("required"));
        assertEquals("ticket.completed", action.get("eventType"));
        assertEquals("T-1", action.get("correlationKey"));
    }

    @Test
    void eventWaitWithoutTheGrantedActionIsNotAdvertised() {
        AgentWorkflowNextActionResolver resolver = resolver();
        AgentWorkflowInstanceVo snapshot = snapshot("WAITING_EVENT", node("wait_1", "{\"eventType\":\"ticket.completed\"}"));

        Map<String, Object> action = resolver.resolve(capability("OBSERVE"), snapshot);

        assertEquals("OBSERVE", action.get("type"));
    }

    @Test
    void failedRunOffersRetryOnItsCurrentNode() {
        AgentWorkflowExternalInvocationService externals = mock(AgentWorkflowExternalInvocationService.class);
        when(externals.listByInstanceId("instance-1")).thenReturn(Collections.emptyList());
        AgentWorkflowInstanceVo snapshot = snapshot("FAILED", node("pay_1", null));

        Map<String, Object> action = new AgentWorkflowNextActionResolver(externals)
                .resolve(capability("RETRY_NODE"), snapshot);

        assertEquals("RETRY_NODE", action.get("type"));
        assertEquals(Arrays.asList("invocationId", "expectedStateVersion", "nodeId"), action.get("required"));
        assertEquals("pay_1", action.get("nodeId"));
    }

    @Test
    void failedRunWithAnUnknownExternalResultWaitsForAHuman() {
        AgentWorkflowExternalInvocationService externals = mock(AgentWorkflowExternalInvocationService.class);
        AgentWorkflowExternalInvocation unknown = new AgentWorkflowExternalInvocation();
        unknown.setStatus("UNKNOWN");
        when(externals.listByInstanceId("instance-1")).thenReturn(Collections.singletonList(unknown));
        AgentWorkflowInstanceVo snapshot = snapshot("FAILED", node("pay_1", null));

        Map<String, Object> action = new AgentWorkflowNextActionResolver(externals)
                .resolve(capability("RETRY_NODE"), snapshot);

        assertEquals("WAIT_HUMAN", action.get("type"));
        assertEquals("EXTERNAL_RESULT_UNKNOWN", action.get("reason"));
        assertEquals(Boolean.FALSE, action.get("retryable"));
    }

    @Test
    void failureToReadExternalInvocationsDoesNotBlockRetry() {
        AgentWorkflowExternalInvocationService externals = mock(AgentWorkflowExternalInvocationService.class);
        when(externals.listByInstanceId(anyString())).thenThrow(new IllegalStateException("db down"));
        AgentWorkflowInstanceVo snapshot = snapshot("FAILED", node("pay_1", null));

        Map<String, Object> action = new AgentWorkflowNextActionResolver(externals)
                .resolve(capability("RETRY_NODE"), snapshot);

        assertEquals("RETRY_NODE", action.get("type"));
    }

    @Test
    void failedRunWithoutTheRetryActionIsTerminal() {
        AgentWorkflowNextActionResolver resolver = resolver();
        AgentWorkflowInstanceVo snapshot = snapshot("FAILED", node("pay_1", null));

        Map<String, Object> action = resolver.resolve(capability("OBSERVE"), snapshot);

        assertEquals("NONE", action.get("type"));
    }

    @Test
    void terminalStatusesAskForNothing() {
        AgentWorkflowNextActionResolver resolver = resolver();

        for (String status : Arrays.asList("COMPLETED", "TERMINATED", "TIMED_OUT")) {
            Map<String, Object> action = resolver.resolve(capability("OBSERVE"), snapshot(status, node("n1", null)));
            assertEquals(Collections.singletonMap("type", "NONE"), action, status);
        }
    }

    @Test
    void runningAndAcceptedInstancesAreObservedAgain() {
        AgentWorkflowNextActionResolver resolver = resolver();

        for (String status : Arrays.asList("ACCEPTED", "RUNNING")) {
            Map<String, Object> action = resolver.resolve(capability("OBSERVE"), instanceAt(status, null));
            assertEquals("OBSERVE", action.get("type"), status);
            assertEquals(Collections.singletonList("invocationId"), action.get("required"), status);
        }
    }

    @Test
    void otherWaitingKindsFallThroughToObserve() {
        AgentWorkflowNextActionResolver resolver = resolver();

        // WAITING_SUBFLOW / WAITING_DELAY 等等待外部推进的状态没有 agent 动作可做，
        // 沿用提取前的「最后一个 else」：观察即可。
        for (String status : Arrays.asList("WAITING_SUBFLOW", "WAITING_DELAY")) {
            Map<String, Object> action = resolver.resolve(capability("OBSERVE"), instanceAt(status, null));
            assertEquals("OBSERVE", action.get("type"), status);
        }
    }

    @Test
    void aDeletedCapabilityBehavesLikeNoGrantedActions() {
        AgentWorkflowNextActionResolver resolver = resolver();

        assertEquals(Collections.singletonMap("type", "WAITING_HUMAN"),
                resolver.resolve(null, snapshot("WAITING_USER", node("fill_1", null))));
        assertEquals(Collections.singletonMap("type", "NONE"),
                resolver.resolve(null, snapshot("FAILED", node("pay_1", null))));
        assertEquals("OBSERVE", resolver.resolve(null, snapshot("RUNNING", null)).get("type"));
    }

    @Test
    void aMissingSnapshotAsksForNothingRatherThanFailing() {
        AgentWorkflowNextActionResolver resolver = resolver();

        assertEquals(Collections.emptyMap(), resolver.resolve(capability("OBSERVE"), null));
    }

    @Test
    void terminalChecksCoverTheInstanceVocabulary() {
        AgentWorkflowNextActionResolver resolver = resolver();

        for (String status : Arrays.asList("COMPLETED", "FAILED", "TERMINATED", "TIMED_OUT")) {
            assertTrue(resolver.isTerminal(status), status);
        }
        for (String status : Arrays.asList("ACCEPTED", "RUNNING", "WAITING_USER", "WAITING_EVENT", "WAITING_SUBFLOW")) {
            assertFalse(resolver.isTerminal(status), status);
        }
        assertFalse(resolver.isTerminal(null));
    }

    @Test
    void mcpApprovalConfigRecognitionAcceptsEitherKey() {
        AgentWorkflowNextActionResolver resolver = resolver();

        assertTrue(resolver.isMcpToolApprovalConfig(
                com.alibaba.fastjson2.JSONObject.parseObject("{\"approvalType\":\"mcp_tool_approval\"}")));
        assertTrue(resolver.isMcpToolApprovalConfig(
                com.alibaba.fastjson2.JSONObject.parseObject("{\"type\":\"mcp_tool_approval\"}")));
        assertFalse(resolver.isMcpToolApprovalConfig(
                com.alibaba.fastjson2.JSONObject.parseObject("{\"approvalType\":\"human\"}")));
        assertFalse(resolver.isMcpToolApprovalConfig(null));
    }

    @Test
    void nonWaitingStatusesNeverTouchTheExternalInvocationStore() {
        AgentWorkflowExternalInvocationService externals = mock(AgentWorkflowExternalInvocationService.class);

        new AgentWorkflowNextActionResolver(externals).resolve(capability("OBSERVE"),
                snapshot("WAITING_EVENT", node("wait_1", "{\"eventType\":\"ticket.completed\"}")));

        verifyNoInteractions(externals);
    }

    @Test
    void waitingUserWithoutANodeDoesNotFail() {
        AgentWorkflowNextActionResolver resolver = resolver();
        AgentWorkflowInstanceVo snapshot = instanceAt("WAITING_USER", "missing_1");

        Map<String, Object> action = resolver.resolve(capability("PROVIDE_AGENT_INPUT"), snapshot);

        assertEquals("WAITING_HUMAN", action.get("type"));
        assertFalse(action.containsKey("nodeId"));
    }

    private AgentWorkflowNextActionResolver resolver() {
        return new AgentWorkflowNextActionResolver(mock(AgentWorkflowExternalInvocationService.class));
    }

    private AgentWorkflowInstanceVo snapshot(String status, AgentWorkflowNodeInstance node) {
        AgentWorkflowInstanceVo snapshot = instanceAt(status, node == null ? null : node.getNodeId());
        if (node != null) snapshot.setNodes(Collections.singletonList(node));
        return snapshot;
    }

    private AgentWorkflowInstanceVo instanceAt(String status, String currentNodeId) {
        AgentWorkflowInstanceVo snapshot = new AgentWorkflowInstanceVo();
        snapshot.setId("instance-1");
        snapshot.setStatus(status);
        snapshot.setCurrentNodeId(currentNodeId);
        return snapshot;
    }

    private AgentWorkflowNodeInstance node(String nodeId, String interactionConfig) {
        AgentWorkflowNodeInstance node = new AgentWorkflowNodeInstance();
        node.setId("node-instance-" + nodeId);
        node.setInstanceId("instance-1");
        node.setNodeId(nodeId);
        node.setNodeType("interaction");
        node.setInteractionConfig(interactionConfig);
        return node;
    }

    private AgentWorkflowCapability capability(String... allowedActions) {
        AgentWorkflowCapability capability = new AgentWorkflowCapability();
        capability.setId("cap-1");
        capability.setAllowedActions(com.alibaba.fastjson2.JSON.toJSONString(Arrays.asList(allowedActions)));
        return capability;
    }
}
