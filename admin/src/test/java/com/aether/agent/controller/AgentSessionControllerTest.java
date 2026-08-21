package com.aether.agent.controller;

import com.aether.agent.entity.AgentSession;
import com.aether.agent.service.AgentConversationService;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.AgentRunPlanService;
import com.aether.agent.service.AgentSessionMemoryService;
import com.aether.agent.service.AgentSessionService;
import com.aether.agent.service.AgentTaskEventService;
import com.aether.agent.service.AgentTaskService;
import com.aether.agent.service.DeepAgentRunService;
import com.aether.agent.service.KnowledgeContextService;
import com.aether.agent.skill.service.SkillContextService;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.local.CurrentUser;
import com.aether.sys.service.AdminPreferenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证 Deep Session 控制器的记忆写入契约。
 */
@ExtendWith(MockitoExtension.class)
class AgentSessionControllerTest {

    @Mock
    private AgentSessionService sessions;
    @Mock
    private AgentTaskService tasks;
    @Mock
    private AgentRunPlanService plans;
    @Mock
    private AgentTaskEventService taskEvents;
    @Mock
    private AgentSessionMemoryService sessionMemories;
    @Mock
    private AdminPreferenceService preferences;
    @Mock
    private DeepAgentRunService deepAgentRuns;
    @Mock
    private AgentDefinitionService agentDefinitions;
    @Mock
    private AgentConversationService conversations;
    @Mock
    private KnowledgeContextService knowledgeContext;
    @Mock
    private SkillContextService skillContextService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    private AgentSessionController controller;

    /**
     * 初始化控制器和当前用户。
     */
    @BeforeEach
    void setUp() {
        HashMap<String, String> user = new HashMap<String, String>();
        user.put("userId", "user-1");
        CurrentUser.set(user);
        controller = new AgentSessionController(sessions, tasks, plans, taskEvents, sessionMemories, preferences,
                deepAgentRuns, agentDefinitions, conversations, knowledgeContext, skillContextService);
        ReflectionTestUtils.setField(controller, "redisTemplate", redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    /**
     * 清理当前用户。
     */
    @AfterEach
    void tearDown() {
        CurrentUser.remove();
    }

    /**
     * 删除记忆必须携带版本头。
     */
    @Test
    void deleteMemoryRequiresIfMatchVersion() {
        when(sessions.getById("session-1")).thenReturn(ownedSession());
        when(valueOperations.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        ServerException error = assertThrows(ServerException.class,
                () -> controller.deleteMemory("session-1", "memory-1", null, "idem-1"));

        assertEquals("400:If-Match 不能为空", error.getMessage());
        verify(redisTemplate).delete(anyString());
        verify(sessionMemories, never()).deleteMemory(anyString(), anyString(), any(), anyString());
    }

    /**
     * 删除记忆会按会话所有权和条目版本执行。
     */
    @Test
    void deleteMemoryPassesExpectedVersionAndCachesSuccess() {
        AgentSession session = ownedSession();
        when(sessions.getById("session-1")).thenReturn(session);
        when(valueOperations.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        WebResponse<Void> response = controller.deleteMemory("session-1", "memory-1", "\"7\"", "idem-1");

        assertEquals(200, response.getCode());
        verify(sessionMemories).deleteMemory("session-1", "memory-1", 7, "用户删除会话记忆");
        verify(valueOperations).set(anyString(), same(response), eq(24L), eq(TimeUnit.HOURS));
    }

    /**
     * 重复的幂等键返回首次成功结果，且不再次删除。
     */
    @Test
    void deleteMemoryReturnsCachedIdempotentResponse() {
        WebResponse<Void> cached = WebResponse.OK((Void) null);
        when(valueOperations.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(false);
        when(valueOperations.get(anyString())).thenReturn(cached);

        WebResponse<Void> response = controller.deleteMemory("session-1", "memory-1", "7", "idem-1");

        assertSame(cached, response);
        verify(sessionMemories, never()).deleteMemory(anyString(), anyString(), any(), anyString());
        verifyNoMoreInteractions(sessions);
    }

    /**
     * 当前用户拥有的会话。
     */
    private AgentSession ownedSession() {
        AgentSession session = new AgentSession();
        session.setId("session-1");
        session.setUserId("user-1");
        session.setDeleted(false);
        return session;
    }
}
