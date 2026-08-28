package com.aether.agent.tools;

import com.aether.agent.entity.AgentConversation;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.service.AgentConversationService;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.tools.entity.ToolResult;
import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HumanHandoffToolTest {
    @Test
    void createsStructuredHandoffAndPausesConversation() {
        AgentConversationService conversations = mock(AgentConversationService.class);
        AgentMessageService messages = mock(AgentMessageService.class);
        AgentConversation conversation = new AgentConversation(); conversation.setId("c-1"); conversation.setDeleted(false);
        when(conversations.getById("c-1")).thenReturn(conversation);
        when(conversations.update(any(), any())).thenReturn(true);
        when(messages.save(any())).thenAnswer(invocation -> { ((AgentMessage) invocation.getArgument(0)).setId("event-1"); return true; });

        ToolResult result = new HumanHandoffTool(conversations, messages).handle("c-1",
                Collections.<String, Object>singletonMap("reason", "REFUND_DISPUTE"));

        assertTrue(result.isWaitingUser());
        assertEquals("HUMAN_HANDOFF", result.getMessage().getInteractionType());
        assertEquals("REFUND_DISPUTE", JSON.parseObject(result.getMessage().getQuestionConfig()).getString("reason"));
        verify(messages).save(any(AgentMessage.class));
    }
}
