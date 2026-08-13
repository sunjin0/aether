package com.aether.agent.controller;

import com.aether.agent.sandbox.dto.SandboxDecisionDto;
import com.aether.agent.sandbox.service.SandboxTaskService;
import com.aether.agent.skill.service.AgentArtifactService;
import com.aether.exception.ServerException;
import com.aether.local.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class SandboxTaskControllerTest {
    @AfterEach void clearUser() { CurrentUser.remove(); }

    @Test void unifiedDecisionApprovesForCurrentRequester() {
        SandboxTaskService tasks = mock(SandboxTaskService.class);
        CurrentUser.set(user("user-1"));
        SandboxDecisionDto request = new SandboxDecisionDto(); request.setDecision("APPROVE"); request.setReason("reviewed");

        new SandboxTaskController(tasks, mock(AgentArtifactService.class), "runner-secret").decision("task-1", request);

        verify(tasks).approve("task-1", "user-1", "reviewed"); verify(tasks, never()).reject(anyString(), anyString(), anyString());
    }

    @Test void unifiedDecisionRejectsForCurrentRequester() {
        SandboxTaskService tasks = mock(SandboxTaskService.class);
        CurrentUser.set(user("user-1"));
        SandboxDecisionDto request = new SandboxDecisionDto(); request.setDecision("reject"); request.setReason("not needed");

        new SandboxTaskController(tasks, mock(AgentArtifactService.class), "runner-secret").decision("task-1", request);

        verify(tasks).reject("task-1", "user-1", "not needed"); verify(tasks, never()).approve(anyString(), anyString(), anyString());
    }

    @Test void unifiedDecisionRejectsUnknownDecisionBeforeCallingService() {
        SandboxTaskService tasks = mock(SandboxTaskService.class);
        SandboxDecisionDto request = new SandboxDecisionDto(); request.setDecision("queue");

        assertThrows(ServerException.class, () -> new SandboxTaskController(tasks, mock(AgentArtifactService.class), "runner-secret").decision("task-1", request));
        verifyNoInteractions(tasks);
    }

    private HashMap<String, String> user(String id) { HashMap<String, String> user = new HashMap<>(); user.put("userId", id); return user; }
}
