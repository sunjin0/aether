package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentSession;
import com.aether.agent.entity.AgentSessionMemory;
import com.aether.agent.mapper.AgentSessionMemoryMapper;
import com.aether.agent.service.AgentDerivedContextInvalidationService;
import com.aether.agent.service.AgentSessionService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证会话记忆治理服务的行为。
 */
@ExtendWith(MockitoExtension.class)
class AgentSessionMemoryServiceImplTest {

    @Mock
    private AgentSessionMemoryMapper memoryMapper;
    @Mock
    private AgentSessionService sessionService;
    @Mock
    private AgentDerivedContextInvalidationService invalidationService;

    private AgentSessionMemoryServiceImpl service;

    /**
     * 处理setUp。
     */
    @BeforeEach
    void setUp() throws Exception {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), AgentSessionMemory.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), AgentSession.class);
        service = new AgentSessionMemoryServiceImpl(sessionService, invalidationService);
        Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(service, memoryMapper);
    }

    /**
     * 用户修正会创建新版本并废弃旧记忆。
     */
    @Test
    void correctMemoryCreatesReplacementAndSupersedesCurrent() {
        AgentSessionMemory current = activeMemory();
        current.setMemoryVersion(2);
        current.setSensitivityLevel("RESTRICTED");
        when(memoryMapper.selectById("memory-1")).thenReturn(current);
        when(memoryMapper.insert(any(AgentSessionMemory.class))).thenAnswer(invocation -> {
            AgentSessionMemory inserted = invocation.getArgument(0);
            inserted.setId("memory-2");
            return 1;
        });
        when(memoryMapper.update(nullable(AgentSessionMemory.class), any(Wrapper.class))).thenReturn(1);
        when(sessionService.update(any(Wrapper.class))).thenReturn(true);

        AgentSessionMemory replacement = service.correctMemory(
                "session-1", "memory-1", "password: secret\n新事实", "用户指出旧事实不准确", 2);

        ArgumentCaptor<AgentSessionMemory> insertCaptor = ArgumentCaptor.forClass(AgentSessionMemory.class);
        verify(memoryMapper).insert(insertCaptor.capture());
        assertEquals("memory-2", replacement.getId());
        assertEquals("session-1", replacement.getSessionId());
        assertEquals("password=[REDACTED] 新事实", replacement.getContent().replace('\n', ' '));
        assertEquals(3, replacement.getMemoryVersion());
        assertEquals(100, replacement.getConfidence());
        assertEquals(AgentSessionMemory.STATUS_ACTIVE, replacement.getStatus());
        assertEquals("RESTRICTED", insertCaptor.getValue().getSensitivityLevel());
        verify(memoryMapper).update(nullable(AgentSessionMemory.class), any(Wrapper.class));
        verify(sessionService).update(any(Wrapper.class));
        verify(invalidationService).invalidateSession("session-1");
    }

    /**
     * 不准确反馈会删除当前记忆并触发会话版本递增。
     */
    @Test
    void inaccurateFeedbackDeletesMemoryAndBumpsSessionVersion() {
        AgentSessionMemory current = activeMemory();
        current.setMemoryVersion(4);
        when(memoryMapper.selectById("memory-1")).thenReturn(current, current);
        when(memoryMapper.update(nullable(AgentSessionMemory.class), any(Wrapper.class))).thenReturn(1);
        when(sessionService.update(any(Wrapper.class))).thenReturn(true);

        AgentSessionMemory removed = service.feedback(
                "session-1", "memory-1", 4, "inaccurate", "用户确认该记忆错误");

        assertEquals("memory-1", removed.getId());
        assertEquals(AgentSessionMemory.STATUS_DELETED, removed.getStatus());
        verify(memoryMapper).update(nullable(AgentSessionMemory.class), any(Wrapper.class));
        verify(sessionService).update(any(Wrapper.class));
        verify(invalidationService).invalidateSession("session-1");
    }

    /**
     * 自动提取记忆写入使用活跃状态和文档范围内重要度。
     */
    @Test
    void recordExtractedMemoryCreatesActiveMemory() {
        when(memoryMapper.insert(any(AgentSessionMemory.class))).thenReturn(1);
        when(sessionService.update(any(Wrapper.class))).thenReturn(true);

        AgentSessionMemory memory = service.recordExtractedMemory(
                "session-1", "fact", "项目使用 Java 8", "message-1", 70, "NORMAL",
                "session-memory-v1", "hash-1", "message-1");

        assertEquals("FACT", memory.getMemoryType());
        assertEquals("项目使用 Java 8", memory.getContent());
        assertEquals("message-1", memory.getSourceMessageId());
        assertEquals("message-1", memory.getSourceEventRange());
        assertEquals("session-memory-v1", memory.getExtractorVersion());
        assertEquals("hash-1", memory.getCandidateHash());
        assertEquals(3, memory.getImportance());
        assertEquals(AgentSessionMemory.STATUS_ACTIVE, memory.getStatus());
        verify(memoryMapper).insert(any(AgentSessionMemory.class));
        verify(sessionService).update(any(Wrapper.class));
        verify(invalidationService).invalidateSession("session-1");
    }

    /**
     * 自然过期会传播到受影响 Session 的派生上下文。
     */
    @Test
    void expireDueMemoriesInvalidatesAffectedSessions() {
        AgentSessionMemory first = new AgentSessionMemory();
        first.setSessionId("session-1");
        AgentSessionMemory duplicate = new AgentSessionMemory();
        duplicate.setSessionId("session-1");
        AgentSessionMemory second = new AgentSessionMemory();
        second.setSessionId("session-2");
        when(memoryMapper.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(first, duplicate, second));
        when(memoryMapper.update(nullable(AgentSessionMemory.class), any(Wrapper.class))).thenReturn(3);
        when(sessionService.update(any(Wrapper.class))).thenReturn(true);

        int updated = service.expireDueMemories();

        assertEquals(3, updated);
        verify(sessionService, times(2)).update(any(Wrapper.class));
        verify(invalidationService, times(1)).invalidateSession("session-1");
        verify(invalidationService, times(1)).invalidateSession("session-2");
    }

    /**
     * 没有到期记忆时不触发派生上下文失效。
     */
    @Test
    void expireDueMemoriesSkipsInvalidationWhenNothingDue() {
        when(memoryMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        int updated = service.expireDueMemories();

        assertEquals(0, updated);
        verify(memoryMapper, never()).update(nullable(AgentSessionMemory.class), any(Wrapper.class));
        verify(sessionService, never()).update(any(Wrapper.class));
        verify(invalidationService, never()).invalidateSession(any());
    }

    /**
     * 自动提取不得创建偏好或保存禁止敏感内容。
     */
    @Test
    void recordExtractedMemoryRejectsPreferenceAndSensitiveContent() {
        assertThrows(com.aether.exception.ServerException.class,
                () -> service.recordExtractedMemory(
                        "session-1", "PREFERENCE", "用户偏好简洁回答", "message-1", 80, "NORMAL"));
        assertThrows(com.aether.exception.ServerException.class,
                () -> service.recordExtractedMemory(
                        "session-1", "FACT", "password: secret123", "message-1", 80, "NORMAL"));

        verify(memoryMapper, never()).insert(any(AgentSessionMemory.class));
    }

    /**
     * 创建活跃记忆。
     */
    private AgentSessionMemory activeMemory() {
        AgentSessionMemory memory = new AgentSessionMemory();
        memory.setId("memory-1");
        memory.setSessionId("session-1");
        memory.setMemoryType("TASK_CONCLUSION");
        memory.setContent("旧事实");
        memory.setImportance(80);
        memory.setConfidence(70);
        memory.setStatus(AgentSessionMemory.STATUS_ACTIVE);
        memory.setDeleted(false);
        return memory;
    }
}
