package com.aether.agent.controller;

import com.aether.agent.entity.AgentConversation;
import com.aether.agent.product.entity.AgentProductProfile;
import com.aether.agent.product.service.AgentProductProfileService;
import com.aether.agent.service.AgentChatService;
import com.aether.agent.service.AgentConversationService;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.service.AgentRunPlanService;
import com.aether.agent.service.AgentRunService;
import com.aether.agent.service.AgentRunStepService;
import com.aether.agent.service.AgentSessionMemoryService;
import com.aether.agent.service.AgentSessionService;
import com.aether.agent.service.AgentTaskEventService;
import com.aether.agent.service.AgentTaskService;
import com.aether.agent.service.ChatAttachmentService;
import com.aether.agent.service.DeepAgentRunService;
import com.aether.agent.service.DeepAgentSigningClient;
import com.aether.agent.service.KnowledgeContextService;
import com.aether.agent.sandbox.service.SandboxTaskService;
import com.aether.agent.skill.service.AgentArtifactService;
import com.aether.agent.skill.service.SkillContextService;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nService;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.storage.service.ObjectStorageService;
import com.aether.sys.entity.ServiceAccount;
import com.aether.sys.service.ServiceAccountService;
import com.aether.workflow.dto.AgentWorkflowTaskListOptions;
import com.aether.workflow.dto.AgentWorkflowTaskListRequest;
import com.aether.workflow.service.AgentWorkflowTaskQueryService;
import com.aether.workflow.vo.AgentWorkflowTaskPage;
import com.aether.workflow.vo.AgentWorkflowTaskVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证服务账号访问会话工作流任务的归属边界与入参边界。
 *
 * <p>接口本身不再挂 {@code @Permission}：Front 的 PermissionAspect 会把服务账号整体 403 掉，
 * 授权改由「服务账号 → 已发布产品 → Agent」与「会话归属」两层推导。
 */
class BusinessAgentControllerWorkflowTasksTest {
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

    @Test
    void theQueryIsScopedToTheRequestingPrincipalAndTheAllowedProducts() {
        Fixture fixture = new Fixture();
        fixture.readableConversation("conv-1");
        fixture.page(task("inv-1"));

        WebResponse<List<AgentWorkflowTaskVo>> response = fixture.controller
                .conversationWorkflowTasks("conv-1", new AgentWorkflowTaskListRequest());

        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        assertEquals(1L, response.getTotal());
        // 作用域只来自会话归属与产品授权，请求体里的任何字段都扩大不了它。
        AgentWorkflowTaskListOptions options = fixture.optionsOf("conv-1");
        assertEquals(1, options.getCurrent());
        assertEquals(20, options.getPageSize());
        assertFalse(options.isIncludeTerminal());
        assertNull(options.getState());
    }

    @Test
    void aConversationOfAnotherPrincipalIsReportedAsMissingRatherThanForbidden() {
        Fixture fixture = new Fixture();
        // 会话归属查询按 principalId 过滤，别人的会话查不到。
        when(fixture.conversations.getOne(any())).thenReturn(null);

        ServerException error = assertThrows(ServerException.class,
                () -> fixture.controller.conversationWorkflowTasks("conv-1", new AgentWorkflowTaskListRequest()));

        // 403 等于告诉无关的人「这个会话是存在的」；与不存在共用 404。
        assertTrue(error.getMessage().startsWith("404:"));
        verify(fixture.tasks, never()).listByConversation(anyString(), any(), any(AgentWorkflowTaskListOptions.class));
    }

    /** 管理员撤销产品授权后，该 Agent 的旧会话不应再通过 Front 暴露。 */
    @Test
    void aConversationWhoseAgentIsNoLongerEntitledIsReportedAsMissing() {
        Fixture fixture = new Fixture();
        AgentConversation conversation = conversation("agent-1");
        when(fixture.conversations.getOne(any())).thenReturn(conversation);
        // 产品已撤下（服务端按 status/deleted 过滤后返回空）。
        when(fixture.products.list(any())).thenReturn(Collections.<AgentProductProfile>emptyList());

        ServerException error = assertThrows(ServerException.class,
                () -> fixture.controller.conversationWorkflowTasks("conv-1", new AgentWorkflowTaskListRequest()));

        assertTrue(error.getMessage().startsWith("404:"));
        verify(fixture.tasks, never()).listByConversation(anyString(), any(), any(AgentWorkflowTaskListOptions.class));
    }

    @Test
    void aRunIdFromAnotherConversationIsRejectedAsNotFound() {
        Fixture fixture = new Fixture();
        fixture.readableConversation("conv-1");
        when(fixture.runs.count(any())).thenReturn(0L);
        AgentWorkflowTaskListRequest request = new AgentWorkflowTaskListRequest();
        request.setRunId("run-from-elsewhere");

        ServerException error = assertThrows(ServerException.class,
                () -> fixture.controller.conversationWorkflowTasks("conv-1", request));

        assertTrue(error.getMessage().startsWith("404:"));
        verify(fixture.tasks, never()).listByConversation(anyString(), any(), any(AgentWorkflowTaskListOptions.class));
    }

    @Test
    void aRunIdOfThisConversationIsForwarded() {
        Fixture fixture = new Fixture();
        fixture.readableConversation("conv-1");
        fixture.page();
        when(fixture.runs.count(any())).thenReturn(1L);
        AgentWorkflowTaskListRequest request = new AgentWorkflowTaskListRequest();
        request.setRunId("run-1");

        fixture.controller.conversationWorkflowTasks("conv-1", request);

        ArgumentCaptor<String> runId = ArgumentCaptor.forClass(String.class);
        verify(fixture.tasks).listByConversation(eq("conv-1"), runId.capture(), any(AgentWorkflowTaskListOptions.class));
        assertEquals("run-1", runId.getValue());
    }

    @Test
    void anOversizedPageSizeIsRejectedInsteadOfSilentlyClamped() {
        Fixture fixture = new Fixture();
        fixture.readableConversation("conv-1");
        AgentWorkflowTaskListRequest request = new AgentWorkflowTaskListRequest();
        request.setPageSize(500L);

        ServerException error = assertThrows(ServerException.class,
                () -> fixture.controller.conversationWorkflowTasks("conv-1", request));

        assertTrue(error.getMessage().startsWith("422:"));
    }

    @Test
    void aMissingBodyFallsBackToTheDefaultPage() {
        Fixture fixture = new Fixture();
        fixture.readableConversation("conv-1");
        fixture.page();

        fixture.controller.conversationWorkflowTasks("conv-1", null);

        AgentWorkflowTaskListOptions options = fixture.optionsOf("conv-1");
        assertEquals(1, options.getCurrent());
        assertEquals(20, options.getPageSize());
        // 不传 state 也不传 includeTerminal 时沿用旧语义「只看未结束的」。
        assertNull(options.getState());
        assertFalse(options.isIncludeTerminal());
    }

    /** 与 Admin 调试页一致：传了 state 就完全接管 includeTerminal。 */
    @Test
    void stateAndPagingAreForwardedSoTheConsoleCanFilter() {
        Fixture fixture = new Fixture();
        fixture.readableConversation("conv-1");
        fixture.page();
        AgentWorkflowTaskListRequest request = new AgentWorkflowTaskListRequest();
        request.setState("finished");
        request.setCurrent(2L);
        request.setPageSize(10L);

        fixture.controller.conversationWorkflowTasks("conv-1", request);

        AgentWorkflowTaskListOptions options = fixture.optionsOf("conv-1");
        assertEquals("finished", options.getState());
        assertEquals(2, options.getCurrent());
        assertEquals(10, options.getPageSize());
    }

    @Test
    void theStateIsMatchedCaseInsensitively() {
        Fixture fixture = new Fixture();
        fixture.readableConversation("conv-1");
        fixture.page();
        AgentWorkflowTaskListRequest request = new AgentWorkflowTaskListRequest();
        request.setState("  FINISHED ");

        fixture.controller.conversationWorkflowTasks("conv-1", request);

        assertTrue(fixture.optionsOf("conv-1").getState().trim().equalsIgnoreCase("finished"));
    }

    /**
     * service 对未知 state 是「静默不筛」，那对界面传错值来说等于悄悄返回全部，
     * 用户会以为筛选生效了。接口这层必须拦住。
     */
    @Test
    void anUnknownStateIsRejectedInsteadOfSilentlyListingEverything() {
        Fixture fixture = new Fixture();
        fixture.readableConversation("conv-1");
        AgentWorkflowTaskListRequest request = new AgentWorkflowTaskListRequest();
        request.setState("done");

        ServerException error = assertThrows(ServerException.class,
                () -> fixture.controller.conversationWorkflowTasks("conv-1", request));

        assertTrue(error.getMessage().startsWith("422:"));
        verify(fixture.tasks, never()).listByConversation(anyString(), any(), any(AgentWorkflowTaskListOptions.class));
    }

    private static class Fixture {
        static final String SERVICE_ACCOUNT_ID = "sa-1";
        static final String PRINCIPAL_ID = "principal-1";

        final ServiceAccountService accounts = mock(ServiceAccountService.class);
        final AgentProductProfileService products = mock(AgentProductProfileService.class);
        final AgentConversationService conversations = mock(AgentConversationService.class);
        final AgentRunService runs = mock(AgentRunService.class);
        final AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        final BusinessAgentController controller;

        Fixture() {
            controller = new BusinessAgentController(accounts, products,
                    mock(AgentDefinitionService.class), mock(AgentChatService.class), runs,
                    mock(AgentRunStepService.class), mock(AgentRunPlanService.class), conversations,
                    mock(AgentMessageService.class), mock(DeepAgentRunService.class),
                    mock(ChatAttachmentService.class), mock(AgentSessionService.class),
                    mock(AgentSessionMemoryService.class), mock(AgentArtifactService.class),
                    mock(SandboxTaskService.class), mock(ObjectStorageService.class), "aether-chat",
                    mock(AgentTaskService.class), mock(AgentTaskEventService.class),
                    mock(DeepAgentSigningClient.class), mock(KnowledgeContextService.class),
                    mock(SkillContextService.class), tasks, mock(ThreadPoolTaskExecutor.class));
            HashMap<String, String> user = new HashMap<String, String>();
            user.put("serviceAccountId", SERVICE_ACCOUNT_ID);
            user.put("principalId", PRINCIPAL_ID);
            CurrentUser.set(user);
            when(accounts.getById(SERVICE_ACCOUNT_ID)).thenReturn(account());
            when(products.list(any())).thenReturn(Collections.singletonList(product("agent-1")));
            when(conversations.getOne(any())).thenReturn(conversation("agent-1"));
        }

        /** 让会话归属与产品授权都成立，接口才能走到查询。 */
        void readableConversation(String conversationId) {
            when(conversations.getOne(any())).thenReturn(conversation("agent-1"));
        }

        void page(AgentWorkflowTaskVo... rows) {
            AgentWorkflowTaskPage page = new AgentWorkflowTaskPage();
            page.setTasks(java.util.Arrays.asList(rows));
            page.setTotal(rows.length);
            when(tasks.listByConversation(anyString(), any(), any(AgentWorkflowTaskListOptions.class))).thenReturn(page);
        }

        /** 捕获实际下发的选项，同时钉住作用域用的会话 ID。 */
        AgentWorkflowTaskListOptions optionsOf(String conversationId) {
            ArgumentCaptor<AgentWorkflowTaskListOptions> captor =
                    ArgumentCaptor.forClass(AgentWorkflowTaskListOptions.class);
            verify(tasks).listByConversation(eq(conversationId), any(), captor.capture());
            return captor.getValue();
        }
    }

    private static ServiceAccount account() {
        ServiceAccount account = new ServiceAccount();
        account.setId(Fixture.SERVICE_ACCOUNT_ID);
        account.setApplicationId("app-1");
        account.setAllowedProductIds("[\"product-1\"]");
        account.setEnabled(true);
        account.setDeleted(false);
        return account;
    }

    private static AgentProductProfile product(String agentDefinitionId) {
        AgentProductProfile product = new AgentProductProfile();
        product.setId("product-1");
        product.setApplicationId("app-1");
        product.setProductType("AGENT");
        product.setAgentDefinitionId(agentDefinitionId);
        product.setStatus(1);
        product.setDeleted(false);
        return product;
    }

    private static AgentConversation conversation(String agentDefinitionId) {
        AgentConversation conversation = new AgentConversation();
        conversation.setId("conv-1");
        conversation.setUserId(Fixture.PRINCIPAL_ID);
        conversation.setAgentDefinitionId(agentDefinitionId);
        conversation.setDeleted(false);
        return conversation;
    }

    private static AgentWorkflowTaskVo task(String invocationId) {
        AgentWorkflowTaskVo task = new AgentWorkflowTaskVo();
        task.setInvocationId(invocationId);
        task.setStatus("RUNNING");
        task.setNextAction(Collections.<String, Object>singletonMap("type", "OBSERVE"));
        return task;
    }
}
