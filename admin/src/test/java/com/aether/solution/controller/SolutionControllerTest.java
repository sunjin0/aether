package com.aether.solution.controller;

import com.aether.agent.application.service.AgentApplicationService;
import com.aether.solution.service.SolutionInstallationService;
import com.aether.solution.service.SolutionService;
import com.aether.agent.service.AgentMcpServerService;
import com.aether.agent.skill.service.AgentSkillService;
import com.aether.knowledge.service.KnowledgeBaseService;
import com.aether.agent.entity.AgentMcpServer;
import com.aether.solution.entity.Solution;
import com.aether.local.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;

class SolutionControllerTest {
    private SolutionController controller() {
        return new SolutionController(mock(SolutionService.class), mock(SolutionInstallationService.class),
                mock(AgentApplicationService.class), mock(AgentMcpServerService.class), mock(AgentSkillService.class));
    }

    @Test
    void manifestAcceptsSupportedUniqueDependencies() {
        Boolean valid = ReflectionTestUtils.invokeMethod(controller(), "validManifest",
                "{\"dependencies\":[{\"type\":\"connector\",\"code\":\"prometheus\",\"version\":\"1\"},{\"type\":\"skill\",\"code\":\"sre-diagnosis\"}]}");
        assertTrue(valid);
    }

    @Test
    void manifestRejectsUnknownDuplicateAndInvalidDependencies() {
        assertFalse(valid("{\"dependencies\":[{\"type\":\"plugin\",\"code\":\"x\"}]}") );
        assertFalse(valid("{\"dependencies\":[{\"type\":\"skill\",\"code\":\"x\"},{\"type\":\"skill\",\"code\":\"x\"}]}") );
        assertFalse(valid("{\"dependencies\":[{\"type\":\"skill\",\"code\":\"x\",\"version\":\"bad version\"}]}") );
    }

    @Test
    void aiSreManifestRequiresAlertAndApprovalConfiguration() {
        assertTrue(valid("{\"capabilities\":[\"alert-webhook\",\"human-approval\"],\"configuration\":{\"alertWebhook\":{\"required\":true},\"approval\":{\"required\":true}}}"));
        assertFalse(valid("{\"capabilities\":[\"alert-webhook\"],\"configuration\":{}}"));
    }

    @Test
    void aiSreManifestRequiresWorkflowAndKnowledgeCodes() {
        assertTrue(valid("{\"capabilities\":[\"diagnosis-workflow\",\"knowledge-retrieval\"],\"configuration\":{\"diagnosisWorkflow\":{\"code\":\"ai-sre-diagnosis\"},\"knowledgeBase\":{\"code\":\"ai-sre-runbooks\"}}}"));
        assertFalse(valid("{\"capabilities\":[\"diagnosis-workflow\"],\"configuration\":{\"diagnosisWorkflow\":{}}}"));
    }

    @Test
    void tenantCannotModifyOrDeleteGlobalSolution() {
        SolutionService solutions = mock(SolutionService.class);
        Solution global = new Solution();
        global.setId("global");
        global.setDeleted(false);
        when(solutions.getById("global")).thenReturn(global);
        SolutionController target = new SolutionController(solutions, mock(SolutionInstallationService.class),
                mock(AgentApplicationService.class), mock(AgentMcpServerService.class), mock(AgentSkillService.class));
        java.util.HashMap<String, String> user = new java.util.HashMap<>();
        user.put("tenantId", "tenant-a");
        CurrentUser.set(user);
        try {
            assertThrows(RuntimeException.class, () -> target.delete("global"));
            Solution update = new Solution();
            update.setId("global"); update.setName("changed"); update.setCode("global"); update.setVersion("1");
            assertTrue(target.save(update).getCode() == 403);
        } finally {
            CurrentUser.remove();
        }
    }

    @Test
    void installationRequiresCompatibleConnectorVersion() {
        AgentMcpServerService connectors = mock(AgentMcpServerService.class);
        AgentMcpServer connector = new AgentMcpServer();
        connector.setCode("prometheus");
        connector.setVersion("1.0.0");
        connector.setStatus(1);
        when(connectors.getOne(any(), org.mockito.ArgumentMatchers.eq(false))).thenReturn(connector);
        SolutionController target = new SolutionController(mock(SolutionService.class), mock(SolutionInstallationService.class),
                mock(AgentApplicationService.class), connectors, mock(AgentSkillService.class));
        Boolean compatible = ReflectionTestUtils.invokeMethod(target, "dependenciesAvailable",
                "{\"dependencies\":[{\"type\":\"connector\",\"code\":\"prometheus\",\"version\":\"1\"}]}", null);
        assertTrue(compatible);
        connector.setVersion("2.0.0");
        Boolean incompatible = ReflectionTestUtils.invokeMethod(target, "dependenciesAvailable",
                "{\"dependencies\":[{\"type\":\"connector\",\"code\":\"prometheus\",\"version\":\"1\"}]}", null);
        assertFalse(incompatible);
    }

    @Test
    void saveRejectsDuplicateCodeAndVersionWithinTenant() {
        SolutionService solutions = mock(SolutionService.class);
        Solution duplicate = new Solution();
        duplicate.setId("existing");
        when(solutions.getOne(any(), org.mockito.ArgumentMatchers.eq(false))).thenReturn(duplicate);
        SolutionController target = new SolutionController(solutions, mock(SolutionInstallationService.class),
                mock(AgentApplicationService.class), mock(AgentMcpServerService.class), mock(AgentSkillService.class));
        java.util.HashMap<String, String> user = new java.util.HashMap<>();
        user.put("tenantId", "tenant-a");
        CurrentUser.set(user);
        try {
            Solution request = new Solution();
            request.setName("SRE"); request.setCode("sre"); request.setVersion("1.0.0");
            assertTrue(target.save(request).getCode() == 409);
        } finally {
            CurrentUser.remove();
        }
    }

    private boolean valid(String manifest) {
        return Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(controller(), "validManifest", manifest));
    }
}
