package com.aether.evaluation.service;

import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatResponse;
import com.aether.agent.model.ModelClient;
import com.aether.agent.model.ModelClientFactory;
import com.aether.agent.service.ModelCatalogService;
import com.aether.evaluation.entity.EvaluationCaseVersion;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmEvaluationGraderTest {
    @Test
    void invalidJudgeJsonIsRetriedBeforeAcceptingAValidResponse() {
        ModelCatalogService catalog = mock(ModelCatalogService.class);
        ModelClientFactory factory = mock(ModelClientFactory.class);
        ModelClient client = mock(ModelClient.class);
        ModelProvider provider = new ModelProvider();
        provider.setDefaultModel("judge-model");
        when(catalog.resolveProvider("judge", "CHAT")).thenReturn(provider);
        when(factory.getClient(provider)).thenReturn(client);
        ModelChatResponse invalid = new ModelChatResponse();
        invalid.setContent("not-json");
        ModelChatResponse valid = new ModelChatResponse();
        valid.setContent("{\"score\":100,\"reason\":\"ok\",\"evidence\":[]}");
        when(client.chatByProvider(any())).thenReturn(invalid, valid);

        LlmEvaluationGrader grader = new LlmEvaluationGrader(catalog, factory);
        LlmEvaluationGrader.Result result = grader.grade(JSONObject.parseObject("{\"modelId\":\"judge\",\"rubric\":\"correctness\"}"),
                new EvaluationCaseVersion(), "actual output");

        assertEquals("PASS", result.getStatus());
        verify(client, times(2)).chatByProvider(any());
    }
}
