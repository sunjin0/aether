package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentSession;
import com.aether.agent.entity.AgentSessionMemory;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatResponse;
import com.aether.agent.model.ModelClient;
import com.aether.agent.model.ModelClientFactory;
import com.aether.agent.service.AgentSessionMemoryService;
import com.aether.agent.service.AgentSessionService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证会话记忆自动提取服务的行为。
 */
@ExtendWith(MockitoExtension.class)
class AgentSessionMemoryExtractionServiceImplTest {
    @Mock
    private AgentSessionService sessionService;
    @Mock
    private AgentSessionMemoryService memoryService;
    @Mock
    private ModelClientFactory modelClientFactory;
    @Mock
    private ModelClient modelClient;

    private AgentSessionMemoryExtractionServiceImpl service;
    private AgentDefinition agent;
    private AgentSession session;
    private ModelProvider provider;

    /**
     * 处理setUp。
     */
    @BeforeEach
    void setUp() {
        service = new AgentSessionMemoryExtractionServiceImpl(sessionService, memoryService, modelClientFactory);
        agent = new AgentDefinition();
        agent.setId("agent-1");
        session = new AgentSession();
        session.setId("session-1");
        provider = new ModelProvider();
        provider.setType("openai");
    }

    /**
     * 用户明确约束会写入会话记忆。
     */
    @Test
    void extractsExplicitUserConstraint() {
        when(sessionService.getOrCreate("conversation-1", "user-1", "agent-1")).thenReturn(session);
        when(memoryService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());

        service.extract("user-1", "conversation-1",
                userMessage("message-1", "请记住：项目需要 Java 8，不能升级到 Java 17。"), agent);

        verify(memoryService).recordExtractedMemory(eq("session-1"), eq("CONSTRAINT"),
                eq("项目需要 Java 8，不能升级到 Java 17"), eq("message-1"), eq(85), eq("NORMAL"),
                eq("session-memory-v1"), anyString(), eq("message-1"));
    }

    /**
     * 合规的模型JSON候选会优先写入。
     */
    @Test
    void extractsModelJsonCandidate() {
        ModelChatResponse response = new ModelChatResponse();
        response.setContent("{\"memories\":[{\"type\":\"FACT\",\"content\":\"项目使用 PostgreSQL 作为主数据库\","
                + "\"confidence\":92,\"sensitivityLevel\":\"NORMAL\",\"sourceEventIds\":[\"message-1\"]}]}");
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chat(any())).thenReturn(response);
        when(sessionService.getOrCreate("conversation-1", "user-1", "agent-1")).thenReturn(session);
        when(memoryService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());

        service.extract("user-1", "conversation-1",
                userMessage("message-1", "项目使用 PostgreSQL 作为主数据库。"), null, agent, provider);

        verify(memoryService).recordExtractedMemory(eq("session-1"), eq("FACT"),
                eq("项目使用 PostgreSQL 作为主数据库"), eq("message-1"), eq(92), eq("NORMAL"),
                eq("session-memory-v1"), anyString(), eq("message-1"));
    }

    /**
     * 敏感内容不会写入持久记忆。
     */
    @Test
    void skipsSensitiveContent() {
        service.extract("user-1", "conversation-1",
                userMessage("message-1", "请记住 password: secret123 项目需要 Java 8"), agent);

        verify(sessionService, never()).getOrCreate(any(), any(), any());
        verify(memoryService, never()).recordExtractedMemory(any(), any(), any(), any(), any(), any(),
                any(), any(), any());
    }

    /**
     * 偏好不会被自动提升为 PREFERENCE。
     */
    @Test
    void doesNotAutoCreatePreferenceMemory() {
        service.extract("user-1", "conversation-1",
                userMessage("message-1", "我偏好简洁回答，以后都这样。"), agent);

        verify(sessionService, never()).getOrCreate(any(), any(), any());
        verify(memoryService, never()).recordExtractedMemory(any(), any(), any(), any(), any(), any(),
                any(), any(), any());
    }

    /**
     * 已有同内容活跃记忆不会重复写入。
     */
    @Test
    void skipsDuplicateActiveMemory() {
        AgentSessionMemory existing = new AgentSessionMemory();
        existing.setMemoryType("CONSTRAINT");
        existing.setContent("项目需要 Java 8，不能升级到 Java 17");
        when(sessionService.getOrCreate("conversation-1", "user-1", "agent-1")).thenReturn(session);
        when(memoryService.list(any(Wrapper.class))).thenReturn(Collections.singletonList(existing));

        service.extract("user-1", "conversation-1",
                userMessage("message-1", "请记住：项目需要 Java 8，不能升级到 Java 17。"), agent);

        verify(memoryService, never()).recordExtractedMemory(any(), any(), any(), any(), any(), any(),
                any(), any(), any());
    }

    /**
     * 创建用户消息。
     */
    private AgentMessage userMessage(String id, String content) {
        AgentMessage message = new AgentMessage();
        message.setId(id);
        message.setRole("user");
        message.setContent(content);
        message.setMessageType("chat");
        return message;
    }
}
