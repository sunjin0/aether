package com.aether.workflow.service.impl;

import com.aether.exception.ServerException;
import com.aether.i18n.I18nService;
import com.aether.i18n.I18nUtils;
import com.aether.workflow.entity.AgentWorkflowCapability;
import com.aether.workflow.entity.AgentWorkflowInvocation;
import com.aether.workflow.runtime.AgentWorkflowNextActionResolver;
import com.aether.workflow.runtime.WorkflowOutputResolver;
import com.aether.workflow.service.AgentDefinitionWorkflowCapabilityBindingService;
import com.aether.workflow.service.AgentWorkflowCapabilityService;
import com.aether.workflow.service.AgentWorkflowEventReceiptService;
import com.aether.workflow.service.AgentWorkflowExecutionService;
import com.aether.workflow.service.AgentWorkflowExternalInvocationService;
import com.aether.workflow.service.AgentWorkflowInstanceService;
import com.aether.workflow.service.AgentWorkflowInvocationCommandService;
import com.aether.workflow.service.AgentWorkflowInvocationRecordService;
import com.aether.workflow.service.AgentWorkflowNodeInstanceService;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.workflow.service.AgentWorkflowVersionService;
import com.aether.workflow.vo.AgentWorkflowInstanceVo;
import com.aether.workflow.vo.AgentWorkflowInvocationObservation;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * observe 与共享 resolver 的接线测试。
 *
 * <p>之所以要钉死这条委派：等待类型（用户输入 / MCP 授权 / 人工）此前只有
 * {@code nextAction} 一处实现，提取后若有人再写回一份，observe 与聊天页的
 * 任务清单就会各说各话，而这种漂移不会有任何编译错误提示。
 */
class AgentWorkflowInvocationServiceImplTest {
    @Test
    void observeDelegatesWaitingKindResolutionToTheSharedResolver() {
        Fixture fixture = new Fixture();
        Map<String, Object> resolved = Collections.singletonMap("type", "RESOLVE_MCP_APPROVAL");
        when(fixture.resolver.resolve(fixture.capability, fixture.snapshot)).thenReturn(resolved);

        AgentWorkflowInvocationObservation observation = fixture.service.observe("inv-1", "user-1", "agent-1");

        verify(fixture.resolver, times(1)).resolve(fixture.capability, fixture.snapshot);
        assertEquals(resolved, observation.getNextAction());
        assertEquals("inv-1", observation.getInvocationId());
        assertEquals("instance-1", observation.getInstanceId());
        assertEquals("WAITING_USER", observation.getStatus());
    }

    @Test
    void observeLoadsTheCapabilityExactlyOncePerCall() {
        Fixture fixture = new Fixture();

        fixture.service.observe("inv-1", "user-1", "agent-1");

        // 提取前 nextAction 内部还会再 getById 一次；resolver 改为接收能力对象后只剩这一次。
        verify(fixture.capabilities, times(1)).getById("cap-1");
    }

    @Test
    void observeUsesTheSnapshotNodesInsteadOfQueryingTheNodeTable() {
        // 这条要用真的 resolver：快照节点解析是它的职责，mock 掉就什么都没验。
        Fixture fixture = Fixture.withRealResolver();
        com.aether.workflow.entity.AgentWorkflowNodeInstance current =
                new com.aether.workflow.entity.AgentWorkflowNodeInstance();
        current.setNodeId("approve_1");
        current.setNodeType("approval");
        fixture.snapshot.setCurrentNodeId("approve_1");
        fixture.snapshot.setNodes(Collections.singletonList(current));
        fixture.snapshot.setVersionNodes("[{\"id\":\"approve_1\",\"name\":\"审批\"}]");

        AgentWorkflowInvocationObservation observation = fixture.service.observe("inv-1", "user-1", "agent-1");

        assertEquals("approval", observation.getCurrentNodeType());
        assertEquals("审批", observation.getCurrentNodeName());
        // 快照里已有节点，不该再按实例主键回查 node_instance。
        verify(fixture.nodes, times(0)).getOne(any());
    }

    @Test
    void observeStillWorksAfterTheCapabilityIsDisabled() {
        Fixture fixture = new Fixture();
        fixture.capability.setEnabled(false);

        // 停用只该拦住新的 START，不能把已经在跑、还等着被观察的调用变成 404。
        AgentWorkflowInvocationObservation observation = fixture.service.observe("inv-1", "user-1", "agent-1");

        assertEquals("WAITING_USER", observation.getStatus());
    }

    @Test
    void observeRejectsAnInvocationOwnedByAnotherPrincipal() {
        Fixture fixture = new Fixture();

        ServerException failure = assertThrows(ServerException.class,
                () -> fixture.service.observe("inv-1", "someone-else", "agent-1"));

        // 状态码是编码在消息前缀里的（见 ServerException(int, String)）。
        assertTrue(failure.getMessage().startsWith("403:"));
    }

    @Test
    void terminalObservationPersistsTheInstanceStatusRatherThanTheProjection() {
        Fixture fixture = new Fixture();
        fixture.snapshot.setStatus("TIMED_OUT");
        when(fixture.resolver.isTerminal("TIMED_OUT")).thenReturn(true);

        AgentWorkflowInvocationObservation observation = fixture.service.observe("inv-1", "user-1", "agent-1");

        assertEquals("TIMED_OUT", observation.getStatus());
        assertEquals("TIMED_OUT", fixture.invocation.getStatus());
        assertEquals("WORKFLOW_TIMED_OUT", observation.getResultCode());
        assertNotNull(fixture.invocation.getCompletedAt());
    }

    /** 手工装配 13 个协作者；本服务当前没有 Spring 上下文测试，构造器即契约。 */
    private static class Fixture {
        final AgentWorkflowCapabilityService capabilities = mock(AgentWorkflowCapabilityService.class);
        final AgentDefinitionWorkflowCapabilityBindingService bindings =
                mock(AgentDefinitionWorkflowCapabilityBindingService.class);
        final AgentWorkflowInvocationRecordService invocationStore =
                mock(AgentWorkflowInvocationRecordService.class);
        final AgentWorkflowExecutionService executions = mock(AgentWorkflowExecutionService.class);
        final AgentWorkflowNodeInstanceService nodes = mock(AgentWorkflowNodeInstanceService.class);
        final WorkflowOutputResolver outputResolver = mock(WorkflowOutputResolver.class);
        final AgentWorkflowNextActionResolver resolver;

        final AgentWorkflowCapability capability = new AgentWorkflowCapability();
        final AgentWorkflowInvocation invocation = new AgentWorkflowInvocation();
        final AgentWorkflowInstanceVo snapshot = new AgentWorkflowInstanceVo();
        final AgentWorkflowInvocationServiceImpl service;

        static Fixture withRealResolver() {
            return new Fixture(new AgentWorkflowNextActionResolver(
                    mock(AgentWorkflowExternalInvocationService.class)));
        }

        Fixture() {
            this(mock(AgentWorkflowNextActionResolver.class));
        }

        Fixture(AgentWorkflowNextActionResolver resolver) {
            this.resolver = resolver;
            // 领域失败路径经 I18nUtils 取文案，而它是 Spring 注入的静态字段。
            new I18nUtils(mock(I18nService.class));

            capability.setId("cap-1");
            capability.setApplicationId("app-1");
            capability.setEnabled(true);
            capability.setStatus(1);
            capability.setAllowedActions("[\"OBSERVE\"]");
            when(capabilities.getById("cap-1")).thenReturn(capability);
            when(bindings.count(any())).thenReturn(1L);

            invocation.setId("inv-1");
            invocation.setCapabilityId("cap-1");
            invocation.setApplicationId("app-1");
            invocation.setWorkflowInstanceId("instance-1");
            invocation.setAgentDefinitionId("agent-1");
            invocation.setPrincipalId("user-1");
            invocation.setStatus("RUNNING");
            invocation.setDeleted(false);
            when(invocationStore.getById("inv-1")).thenReturn(invocation);

            snapshot.setId("instance-1");
            snapshot.setStatus("WAITING_USER");
            snapshot.setCurrentNodeId("fill_1");
            when(executions.detail("instance-1", "user-1")).thenReturn(snapshot);

            service = new AgentWorkflowInvocationServiceImpl(capabilities, bindings,
                    mock(AgentWorkflowService.class), mock(AgentWorkflowVersionService.class),
                    mock(AgentWorkflowInstanceService.class), nodes, executions, outputResolver,
                    invocationStore, mock(AgentWorkflowExternalInvocationService.class),
                    mock(AgentWorkflowEventReceiptService.class),
                    mock(AgentWorkflowInvocationCommandService.class), resolver);
        }
    }
}
