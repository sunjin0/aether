package com.aether.workflow.controller;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nService;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.workflow.dto.AgentWorkflowCapabilityBindingRequest;
import com.aether.workflow.entity.AgentDefinitionWorkflowCapabilityBinding;
import com.aether.workflow.entity.AgentWorkflowCapability;
import com.aether.workflow.service.AgentDefinitionWorkflowCapabilityBindingService;
import com.aether.workflow.service.AgentWorkflowCapabilityService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 Agent 工作流能力绑定控制器的行为。
 */
@ExtendWith(MockitoExtension.class)
class AgentDefinitionWorkflowCapabilityBindingControllerTest {

    private static final String DEEP_UNSUPPORTED_KEY = "agent.workflow.capability.binding.deep.unsupported";

    @Mock
    private AgentDefinitionWorkflowCapabilityBindingService bindingService;
    @Mock
    private AgentWorkflowCapabilityService capabilityService;
    @Mock
    private AgentDefinitionService agentDefinitionService;
    @Mock
    private I18nService i18nService;

    private AgentDefinitionWorkflowCapabilityBindingController controller;

    @BeforeEach
    void setUp() {
        // lambdaQuery 需要先注册实体元信息，否则构建 Wrapper 时会报 “can not find lambda cache”。
        initTableInfo(AgentDefinitionWorkflowCapabilityBinding.class);
        new I18nUtils(i18nService);
        lenient().when(i18nService.getMessage(anyString())).thenReturn("ok");
        lenient().when(i18nService.getMessage(DEEP_UNSUPPORTED_KEY)).thenReturn("Deep 智能体暂不支持绑定工作流能力");

        HashMap<String, String> user = new HashMap<String, String>();
        user.put("userId", "user-1");
        user.put("tenantId", "tenant-1");
        CurrentUser.set(user);

        controller = new AgentDefinitionWorkflowCapabilityBindingController(
                bindingService, capabilityService, agentDefinitionService);
    }

    @AfterEach
    void tearDown() {
        CurrentUser.remove();
    }

    /**
     * Deep 智能体不能新建工作流能力绑定，且必须在落库前被拦下。
     */
    @Test
    void deepAgentCannotBindWorkflowCapability() {
        stubAgent("DEEP");

        AgentWorkflowCapabilityBindingRequest request = bindingRequest("capability-1");
        ServerException failure = assertThrows(ServerException.class, () -> controller.bind("agent-1", request));

        assertTrue(failure.getMessage().startsWith("409:"), "应以 409 拒绝，实际：" + failure.getMessage());
        verify(i18nService).getMessage(DEEP_UNSUPPORTED_KEY);
        verify(bindingService, never()).save(any());
    }

    /**
     * 存量绑定仍需保留清理手段：Deep 智能体可以解绑与停用。
     */
    @Test
    void deepAgentCanStillUnbindAndDisable() {
        stubAgent("DEEP");
        when(bindingService.remove(any(Wrapper.class))).thenReturn(true);
        when(bindingService.update(any(Wrapper.class))).thenReturn(true);

        controller.unbind("agent-1", "capability-1");
        verify(bindingService, never()).save(any());
        verify(bindingService).remove(any(Wrapper.class));

        controller.status("agent-1", "capability-1", bindingRequest("capability-1"));
        verify(bindingService).update(any(Wrapper.class));
    }

    /**
     * 普通智能体的绑定不受影响。
     */
    @Test
    void standardAgentCanStillBind() {
        stubAgent("STANDARD");
        AgentWorkflowCapability capability = new AgentWorkflowCapability();
        capability.setId("capability-1");
        capability.setApplicationId("application-1");
        capability.setEnabled(Boolean.TRUE);
        when(capabilityService.getById("capability-1")).thenReturn(capability);
        when(bindingService.count(any(Wrapper.class))).thenReturn(0L);
        when(bindingService.save(any())).thenReturn(true);

        controller.bind("agent-1", bindingRequest("capability-1"));

        verify(bindingService).save(any());
    }

    private void stubAgent(String executionMode) {
        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-1");
        agent.setApplicationId("application-1");
        agent.setTenantId("tenant-1");
        agent.setExecutionMode(executionMode);
        lenient().when(agentDefinitionService.getById("agent-1")).thenReturn(agent);
    }

    private AgentWorkflowCapabilityBindingRequest bindingRequest(String capabilityId) {
        AgentWorkflowCapabilityBindingRequest request = new AgentWorkflowCapabilityBindingRequest();
        request.setCapabilityId(capabilityId);
        return request;
    }

    private void initTableInfo(Class<?> type) {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
    }
}
