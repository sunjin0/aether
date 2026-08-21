package com.aether.knowledge.service.impl;

import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatRequest;
import com.aether.agent.model.ModelChatResponse;
import com.aether.agent.model.ModelClient;
import com.aether.agent.model.ModelClientFactory;
import com.aether.agent.service.ModelCatalogService;
import com.aether.knowledge.entity.KnowledgeBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentMarkdownFormatterTest {
    @Mock
    private ModelCatalogService modelCatalogService;
    @Mock
    private ModelClientFactory modelClientFactory;
    @Mock
    private ModelClient modelClient;

    @Test
    void formatSplitsLongContentAndMergesFormattedChunks() {
        KnowledgeDocumentMarkdownFormatter formatter = new KnowledgeDocumentMarkdownFormatter(modelCatalogService, modelClientFactory);
        KnowledgeBase base = new KnowledgeBase();
        base.setReviewConfig("{\"reviewModelId\":\"model-1\"}");
        ModelProvider provider = new ModelProvider();
        provider.setDefaultModel("test-model");
        ModelChatResponse first = new ModelChatResponse();
        first.setContent("# First");
        ModelChatResponse second = new ModelChatResponse();
        second.setContent("## Second");
        when(modelCatalogService.resolveProvider("model-1", "CHAT,MULTIMODAL")).thenReturn(provider);
        when(modelClientFactory.getClient(provider)).thenReturn(modelClient);
        when(modelClient.chatByProvider(any())).thenReturn(first, second);

        String content = repeat('a', 39990) + "\n" + repeat('b', 100);
        String formatted = formatter.format(base, "Long document", content);

        assertEquals("# First\n\n## Second", formatted);
        ArgumentCaptor<ModelChatRequest> requests = ArgumentCaptor.forClass(ModelChatRequest.class);
        verify(modelClient, times(2)).chatByProvider(requests.capture());
        List<ModelChatRequest> values = requests.getAllValues();
        assertTrue(values.get(0).getMessages().get(1).getContent().contains("第1/2个连续片段"));
        assertTrue(values.get(1).getMessages().get(1).getContent().contains("第2/2个连续片段"));
    }

    @Test
    void splitContentPrefersNewlineBoundaryWithoutDroppingText() {
        String content = repeat('a', 39990) + "\n" + repeat('b', 100);

        List<String> chunks = KnowledgeDocumentMarkdownFormatter.splitContent(content);

        assertEquals(2, chunks.size());
        assertEquals(content, String.join("", chunks));
    }

    private String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) builder.append(value);
        return builder.toString();
    }
}
