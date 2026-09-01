package com.aether.tenant.controller;

import com.aether.agent.application.service.AgentApplicationService;
import com.aether.local.CurrentUser;
import com.aether.tenant.entity.Project;
import com.aether.tenant.entity.Workspace;
import com.aether.tenant.service.ProjectService;
import com.aether.tenant.service.WorkspaceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ProjectControllerTenantBoundaryTest {
    @AfterEach
    void clearCurrentUser() { CurrentUser.remove(); }

    @Test
    void rejectsProjectWriteForWorkspaceOfAnotherTenant() {
        HashMap<String, String> user = new HashMap<>(); user.put("tenantId", "tenant-a"); CurrentUser.set(user);
        ProjectService projects = mock(ProjectService.class);
        WorkspaceService workspaces = mock(WorkspaceService.class);
        AgentApplicationService applications = mock(AgentApplicationService.class);
        Workspace workspace = new Workspace(); workspace.setId("workspace-b"); workspace.setTenantId("tenant-b"); workspace.setStatus(1); workspace.setDeleted(false);
        when(workspaces.getById("workspace-b")).thenReturn(workspace);
        Project request = new Project(); request.setWorkspaceId("workspace-b"); request.setCode("portal"); request.setName("Portal");

        assertThrows(RuntimeException.class, () -> new ProjectController(projects, workspaces, applications).save(request));
        verify(projects, never()).save(any(Project.class));
        verifyNoInteractions(applications);
    }

    @Test
    void rejectsProjectWriteForDisabledApplication() {
        ProjectService projects = mock(ProjectService.class);
        WorkspaceService workspaces = mock(WorkspaceService.class);
        AgentApplicationService applications = mock(AgentApplicationService.class);
        Workspace workspace = new Workspace(); workspace.setId("workspace-a"); workspace.setTenantId("tenant-a"); workspace.setStatus(1); workspace.setDeleted(false);
        when(workspaces.getById("workspace-a")).thenReturn(workspace);
        com.aether.agent.application.entity.AgentApplication application = new com.aether.agent.application.entity.AgentApplication();
        application.setId("app-a"); application.setTenantId("tenant-a"); application.setStatus(0); application.setDeleted(false);
        when(applications.getById("app-a")).thenReturn(application);
        Project request = new Project(); request.setWorkspaceId("workspace-a"); request.setApplicationId("app-a"); request.setCode("portal"); request.setName("Portal");

        assertThrows(RuntimeException.class, () -> new ProjectController(projects, workspaces, applications).save(request));
        verify(projects, never()).save(any(Project.class));
    }
}
