package com.aether.agent.service;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatRunServiceTest {

    @Mock
    private AgentRunService agentRunService;

    @Test
    void persistsProviderRawResponseForRunAudit() {
        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-1");
        agent.setModel("gpt-4.1-mini");
        ModelProvider provider = new ModelProvider();
        provider.setId("provider-1");
        ModelChatResponse response = new ModelChatResponse();
        response.setContent("answer");
        response.setRawResponse("{\"usage\":{\"prompt_tokens\":100,\"prompt_tokens_details\":{\"cached_tokens\":75}}}");

        new ChatRunService(agentRunService).create(agent, provider, "user-1", "conversation-1",
                "message-1", "input", response, 100L, 0, null);

        ArgumentCaptor<AgentRun> saved = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunService).save(saved.capture());
        assertEquals(response.getRawResponse(), saved.getValue().getRawResponse());
    }
}
