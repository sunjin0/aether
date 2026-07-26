package com.aether.agent.service;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatMessage;
import com.aether.agent.model.ModelChatRequest;
import com.aether.agent.model.ModelChatResponse;
import com.aether.agent.model.ModelClient;
import com.aether.agent.model.ModelClientFactory;
import com.aether.agent.model.QueryRewriteResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryRewriteServiceTest {

    @Mock
    private ModelClientFactory modelClientFactory;
    @Mock
    private ModelClient modelClient;

    @Test
    void rewritesCurrentRawMessageUsingOnlyConversationHistory() {
        ModelProvider provider = new ModelProvider();
        provider.setId("provider-1");
        ModelChatResponse response = new ModelChatResponse();
        response.setContent("{\"rewrittenContent\":\"企业版产品的退款期限是多少？\"}");
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chat(any(ModelChatRequest.class))).thenReturn(response);

        QueryRewriteResult result = new QueryRewriteService(modelClientFactory).rewrite(Arrays.asList(
                new ModelChatMessage("user", "标准版退款期限是多少？"),
                new ModelChatMessage("assistant", "标准版为 7 天。")),
                "企业版呢？", new AgentDefinition(), provider);

        assertEquals("企业版产品的退款期限是多少？", result.getRewrittenContent());
        ArgumentCaptor<ModelChatRequest> request = ArgumentCaptor.forClass(ModelChatRequest.class);
        verify(modelClient).chat(request.capture());
        assertEquals(0, request.getValue().getTemperature().compareTo(java.math.BigDecimal.ZERO));
        assertEquals("json_object", request.getValue().getResponseFormat().get("type"));
        String prompt = request.getValue().getMessages().get(0).getContent();
        org.junit.jupiter.api.Assertions.assertTrue(prompt.contains("企业版呢？"));
        org.junit.jupiter.api.Assertions.assertTrue(prompt.contains("标准版退款期限是多少？"));
    }

    @Test
    void invalidModelResponseLeavesRewriteEmptyForOriginalFallback() {
        ModelProvider provider = new ModelProvider();
        ModelChatResponse response = new ModelChatResponse();
        response.setContent("not json");
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chat(any(ModelChatRequest.class))).thenReturn(response);

        QueryRewriteResult result = new QueryRewriteService(modelClientFactory).rewrite(
                null, "退款期限是多少？", new AgentDefinition(), provider);

        assertNull(result.getRewrittenContent());
    }
}
