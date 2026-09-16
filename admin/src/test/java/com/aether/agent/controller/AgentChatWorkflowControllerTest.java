package com.aether.agent.controller;

import com.aether.agent.entity.AgentConversation;
import com.aether.agent.service.AgentConversationService;
import com.aether.agent.service.AgentRunService;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nService;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.permission.Permission;
import com.aether.workflow.dto.AgentWorkflowTaskListRequest;
import com.aether.workflow.service.AgentWorkflowTaskQueryService;
import com.aether.workflow.vo.AgentWorkflowTaskPage;
import com.aether.workflow.vo.AgentWorkflowTaskVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证聊天页工作流任务接口的权限、归属与入参边界。 */
class AgentChatWorkflowControllerTest {
    @BeforeEach
    void setUpI18n() {
        I18nService i18n = mock(I18nService.class);
        when(i18n.getMessage(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        ReflectionTestUtils.setField(I18nUtils.class, "i18nService", i18n);
    }

    @AfterEach
    void tearDown() {
        CurrentUser.remove();
        ReflectionTestUtils.setField(I18nUtils.class, "i18nService", null);
    }

    /**
     * 聊天页的使用者没有编排台权限；门控一旦写回 /workflow/run，本功能的目标用户会被整体 403。
     */
    @Test
    void theEndpointIsGatedOnTheChatPermissionRatherThanTheWorkflowConsoleOne() throws Exception {
        Permission typeLevel = AgentChatWorkflowController.class.getAnnotation(Permission.class);
        assertEquals("/agent/chat", typeLevel.path());

        Method tasks = AgentChatWorkflowController.class.getMethod("tasks", String.class,
                AgentWorkflowTaskListRequest.class);
        assertEquals(null, tasks.getAnnotation(Permission.class));
    }

    @Test
    void theQueryIsScopedToTheRequestingUserInsteadOfAnAgentIdFromTheClient() {
        Fixture fixture = new Fixture();
        fixture.ownedConversation("conv-1", "user-1");
        fixture.page(task("inv-1"));

        WebResponse<java.util.List<AgentWorkflowTaskVo>> response = fixture.controller
                .tasks("conv-1", new AgentWorkflowTaskListRequest());

        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        verify(fixture.tasks).listByConversation("conv-1", null, false, 1, 20);
    }

    @Test
    void aConversationIsReportedAsMissingRatherThanForbiddenWhenItBelongsToSomeoneElse() {
        Fixture fixture = new Fixture();
        fixture.ownedConversation("conv-1", "someone-else");

        ServerException error = assertThrows(ServerException.class,
                () -> fixture.controller.tasks("conv-1", new AgentWorkflowTaskListRequest()));

        // 403 等于告诉无关的人「这个会话是存在的」；与不存在共用 404。
        assertTrue(error.getMessage().startsWith("404:"));
        verify(fixture.tasks, never()).listByConversation(anyString(), any(), anyBoolean(), anyInt(), anyInt());
    }

    @Test
    void aSoftDeletedConversationIsAlsoReportedAsMissing() {
        Fixture fixture = new Fixture();
        AgentConversation conversation = fixture.ownedConversation("conv-1", "user-1");
        conversation.setDeleted(true);

        ServerException error = assertThrows(ServerException.class,
                () -> fixture.controller.tasks("conv-1", new AgentWorkflowTaskListRequest()));

        assertTrue(error.getMessage().startsWith("404:"));
    }

    @Test
    void aRunIdFromAnotherConversationIsRejectedAsNotFound() {
        Fixture fixture = new Fixture();
        fixture.ownedConversation("conv-1", "user-1");
        // 运行属于别的会话：count 命中 0 行。
        when(fixture.runs.count(any())).thenReturn(0L);
        AgentWorkflowTaskListRequest request = new AgentWorkflowTaskListRequest();
        request.setRunId("run-from-elsewhere");

        ServerException error = assertThrows(ServerException.class, () -> fixture.controller.tasks("conv-1", request));

        assertTrue(error.getMessage().startsWith("404:"));
        verify(fixture.tasks, never()).listByConversation(anyString(), any(), anyBoolean(), anyInt(), anyInt());
    }

    @Test
    void anOversizedPageSizeIsRejectedInsteadOfSilentlyClamped() {
        Fixture fixture = new Fixture();
        fixture.ownedConversation("conv-1", "user-1");
        AgentWorkflowTaskListRequest request = new AgentWorkflowTaskListRequest();
        request.setPageSize(500L);

        ServerException error = assertThrows(ServerException.class, () -> fixture.controller.tasks("conv-1", request));

        assertEquals(true, error.getMessage().startsWith("422:"));
    }

    @Test
    void aMissingBodyFallsBackToTheDefaultPage() {
        Fixture fixture = new Fixture();
        fixture.ownedConversation("conv-1", "user-1");
        fixture.page();

        fixture.controller.tasks("conv-1", null);

        verify(fixture.tasks).listByConversation("conv-1", null, false, 1, 20);
    }

    /** 打开历史会话时才有必要翻出终态行。 */
    @Test
    void includeTerminalIsForwardedSoHistoryCanBeListed() {
        Fixture fixture = new Fixture();
        fixture.ownedConversation("conv-1", "user-1");
        fixture.page();
        AgentWorkflowTaskListRequest request = new AgentWorkflowTaskListRequest();
        request.setIncludeTerminal(true);
        request.setCurrent(2L);
        request.setPageSize(10L);

        fixture.controller.tasks("conv-1", request);

        verify(fixture.tasks).listByConversation("conv-1", null, true, 2, 10);
    }

    private static class Fixture {
        final AgentConversationService conversations = mock(AgentConversationService.class);
        final AgentRunService runs = mock(AgentRunService.class);
        final AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        final AgentChatWorkflowController controller;

        Fixture() {
            controller = new AgentChatWorkflowController(conversations, runs, tasks);
            HashMap<String, String> user = new HashMap<>();
            user.put("userId", "user-1");
            CurrentUser.set(user);
        }

        AgentConversation ownedConversation(String conversationId, String ownerId) {
            AgentConversation conversation = new AgentConversation();
            conversation.setId(conversationId);
            conversation.setUserId(ownerId);
            conversation.setAgentDefinitionId("agent-1");
            when(conversations.getById(conversationId)).thenReturn(conversation);
            return conversation;
        }

        void page(AgentWorkflowTaskVo... rows) {
            AgentWorkflowTaskPage page = new AgentWorkflowTaskPage();
            page.setTasks(java.util.Arrays.asList(rows));
            page.setTotal(rows.length);
            when(tasks.listByConversation(anyString(), any(), anyBoolean(), anyInt(), anyInt())).thenReturn(page);
        }
    }

    private static AgentWorkflowTaskVo task(String invocationId) {
        AgentWorkflowTaskVo task = new AgentWorkflowTaskVo();
        task.setInvocationId(invocationId);
        task.setStatus("RUNNING");
        task.setNextAction(Collections.<String, Object>singletonMap("type", "OBSERVE"));
        return task;
    }
}
