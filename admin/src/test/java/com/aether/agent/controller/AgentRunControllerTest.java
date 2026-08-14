package com.aether.agent.controller;

import com.aether.agent.service.AgentRunService;
import com.aether.agent.service.AgentRunStepService;
import com.aether.agent.service.DeepAgentSigningClient;
import com.aether.agent.service.AgentRunPlanService;
import com.aether.agent.service.DeepAgentRunService;
import com.aether.agent.dto.DeepAgentConfig;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.vo.AgentRunStatisticsVo;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nService;
import com.aether.i18n.I18nUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class AgentRunControllerTest {

    @BeforeEach
    void setUpI18n() {
        I18nService i18n = org.mockito.Mockito.mock(I18nService.class);
        org.mockito.Mockito.lenient().when(i18n.getMessage(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ReflectionTestUtils.setField(I18nUtils.class, "i18nService", i18n);
    }

    @Mock
    private AgentRunService agentRunService;

    @Mock
    private AgentRunStepService agentRunStepService;

    @Mock
    private DeepAgentSigningClient signingClient;

    @Mock
    private DeepAgentConfig deepAgentConfig;

    @Mock
    private AgentRunPlanService planService;

    @Mock
    private DeepAgentRunService deepAgentRunService;

    @Test
    void statisticsBindsAndForwardsAgentDefinitionId() throws Exception {
        when(agentRunService.statistics("agent-definition-1", 100L, 200L))
                .thenReturn(new AgentRunStatisticsVo());

        AgentRunController controller = controller();
        controller.statistics("agent-definition-1", 100L, 200L);

        verify(agentRunService).statistics("agent-definition-1", 100L, 200L);
    }

    @Test
    void cancelReturnsBadGatewayWhenExternalCancellationFails() {
        AgentRun run = new AgentRun();
        run.setId("deep-run-1");
        run.setExecutionMode("DEEP");
        run.setDeleted(false);
        when(agentRunService.getById("deep-run-1")).thenReturn(run);
        doThrow(new IllegalStateException("Deep Agent returned HTTP 503"))
                .when(signingClient).signedPost(org.mockito.ArgumentMatchers.eq("/v1/runs/deep-run-1/cancel"),
                        org.mockito.ArgumentMatchers.anyMap());

        ServerException error = assertThrows(ServerException.class, () -> controller().cancel("deep-run-1"));
        assertEquals(true, error.getMessage().startsWith("502:"));
    }

    private AgentRunController controller() {
        return new AgentRunController(agentRunService, agentRunStepService, signingClient, deepAgentConfig, planService, deepAgentRunService);
    }
}
