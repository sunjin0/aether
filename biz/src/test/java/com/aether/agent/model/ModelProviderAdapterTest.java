package com.aether.agent.model;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.adapter.AnthropicChatAdapter;
import com.aether.agent.model.adapter.AzureOpenAIChatAdapter;
import com.aether.agent.model.adapter.ModelProviderAdapter;
import com.aether.agent.model.adapter.OpenAIChatAdapter;
import com.aether.agent.model.adapter.QwenOpenAICompatibleAdapter;
import com.aether.utils.AesUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies provider-specific chat adapter behavior.
 */
class ModelProviderAdapterTest {

    @Test
    void openAiAdapterBuildsCompatibleBodyAndParsesStreamToolCalls() throws Exception {
        OpenAIChatAdapter adapter = new OpenAIChatAdapter();
        ModelChatRequest request = request("openai", "gpt-4.1-mini");
        request.setTemperature(new BigDecimal("0.2"));
        request.setReasoningEffort("medium");
        request.setResponseFormat(Collections.<String, Object>singletonMap("type", "json_object"));
        request.setTools(Collections.singletonList(tool()));

        JSONObject body = adapter.body(request, true);

        assertEquals("gpt-4.1-mini", body.getString("model"));
        assertEquals(true, body.getBoolean("stream"));
        assertEquals("medium", body.getString("reasoning_effort"));
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"));
        assertEquals("auto", body.getString("tool_choice"));
        assertEquals("lookup", body.getJSONArray("tools").getJSONObject(0).getJSONObject("function").getString("name"));

        String stream = "data: {\"model\":\"gpt-4.1-mini\",\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"lookup\",\"arguments\":\"{\\\"q\\\":\"}}]}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"a\\\"}\"}}]}}]}\n\n"
                + "data: {\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":4,\"total_tokens\":7,\"completion_tokens_details\":{\"reasoning_tokens\":2}},\"choices\":[]}\n\n"
                + "data: [DONE]\n\n";
        ModelStreamResponse response = adapter.parseStream(new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8)), "fallback", null);

        assertEquals("hi", response.getContent());
        assertEquals(7, response.getTotalTokens());
        assertEquals(2, response.getReasoningTokens());
        JSONArray calls = JSONArray.parseArray(response.getToolCalls());
        assertEquals("{\"q\":\"a\"}", calls.getJSONObject(0).getJSONObject("function").getString("arguments"));
    }

    @Test
    void qwenAdapterFiltersUnsupportedOpenAiExtrasButKeepsReasoningAndTools() {
        QwenOpenAICompatibleAdapter adapter = new QwenOpenAICompatibleAdapter();
        ModelChatRequest request = request("qwen-compatible", "qwen-plus");
        request.setSeed(42);
        request.setLogprobs(true);
        request.setResponseFormat(Collections.<String, Object>singletonMap("type", "json_object"));
        request.setReasoningEffort("low");
        request.setTools(Collections.singletonList(tool()));

        JSONObject body = adapter.body(request, false);

        assertEquals("qwen-plus", body.getString("model"));
        assertEquals("low", body.getString("reasoning_effort"));
        assertTrue(body.containsKey("tools"));
        assertFalse(body.containsKey("seed"));
        assertFalse(body.containsKey("logprobs"));
        assertFalse(body.containsKey("response_format"));
    }

    @Test
    void azureAdapterUsesDeploymentUrlApiKeyAndDropsModelFromBody() {
        AzureOpenAIChatAdapter adapter = new AzureOpenAIChatAdapter();
        ModelChatRequest request = request("azure-openai", "gpt-4o");
        request.getProvider().setApiBaseUrl("https://example.openai.azure.com");
        Map<String, Object> options = new HashMap<>();
        options.put("deployment", "prod-chat");
        options.put("apiVersion", "2024-06-01");
        request.setProviderOptions(options);

        assertEquals("https://example.openai.azure.com/openai/deployments/prod-chat/chat/completions?api-version=2024-06-01",
                adapter.chatUrl(request));
        assertTrue(adapter.headers(request).containsKey("api-key"));
        assertFalse(adapter.body(request, false).containsKey("model"));
    }

    @Test
    void anthropicAdapterMapsToolsMessagesAndResponses() throws Exception {
        AnthropicChatAdapter adapter = new AnthropicChatAdapter();
        ModelChatRequest request = request("anthropic", "claude-3-5-sonnet-latest");
        request.setMaxCompletionTokens(512);
        request.setToolChoiceName("lookup");
        request.setTools(Collections.singletonList(tool()));

        JSONObject body = adapter.body(request, false);

        assertEquals("claude-3-5-sonnet-latest", body.getString("model"));
        assertEquals("You are concise.", body.getString("system"));
        assertEquals(512, body.getInteger("max_tokens"));
        assertEquals("lookup", body.getJSONObject("tool_choice").getString("name"));
        assertEquals("lookup", body.getJSONArray("tools").getJSONObject(0).getString("name"));

        String responseBody = "{\"model\":\"claude-3-5-sonnet-latest\",\"content\":["
                + "{\"type\":\"text\",\"text\":\"hello\"},"
                + "{\"type\":\"thinking\",\"thinking\":\"plan\"},"
                + "{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"lookup\",\"input\":{\"q\":\"a\"}}"
                + "],\"usage\":{\"input_tokens\":5,\"output_tokens\":6}}";
        ModelChatResponse response = adapter.parseResponse(responseBody, "fallback");

        assertEquals("hello", response.getContent());
        assertEquals("plan", response.getReasoningContent());
        assertEquals(11, response.getTotalTokens());
        assertEquals("lookup", JSONArray.parseArray(response.getToolCalls()).getJSONObject(0).getJSONObject("function").getString("name"));

        String stream = "event: message_start\n"
                + "data: {\"message\":{\"model\":\"claude-3-5-sonnet-latest\",\"usage\":{\"input_tokens\":2}}}\n\n"
                + "event: content_block_delta\n"
                + "data: {\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"he\"}}\n\n"
                + "event: content_block_start\n"
                + "data: {\"index\":1,\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"lookup\"}}\n\n"
                + "event: content_block_delta\n"
                + "data: {\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"q\\\":\\\"a\\\"}\"}}\n\n"
                + "event: message_delta\n"
                + "data: {\"usage\":{\"output_tokens\":3}}\n\n";
        ModelStreamResponse streamResponse = adapter.parseStream(new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8)), "fallback", null);

        assertEquals("he", streamResponse.getContent());
        assertEquals(5, streamResponse.getTotalTokens());
        assertEquals("{\"q\":\"a\"}", JSONArray.parseArray(streamResponse.getToolCalls()).getJSONObject(0)
                .getJSONObject("function").getString("arguments"));
    }

    @Test
    void modelClientRoutesAllBuiltInAdapters() {
        OpenAIModelClient client = new OpenAIModelClient(new PooledHttpClient(), Arrays.<ModelProviderAdapter>asList(
                new OpenAIChatAdapter(),
                new AzureOpenAIChatAdapter(),
                new AnthropicChatAdapter(),
                new QwenOpenAICompatibleAdapter()
        ));

        assertTrue(client.supports("openai"));
        assertTrue(client.supports("local-openai-compatible"));
        assertTrue(client.supports("azure-openai"));
        assertTrue(client.supports("anthropic"));
        assertTrue(client.supports("qwen-compatible"));
        assertFalse(client.supports("unsupported"));
    }

    private ModelChatRequest request(String providerType, String model) {
        ModelProvider provider = new ModelProvider();
        provider.setType(providerType);
        provider.setApiBaseUrl("https://api.example.com");
        provider.setApiKey(AesUtil.encrypt("plain-key"));
        provider.setName(providerType);

        AgentDefinition agent = new AgentDefinition();
        agent.setModel(model);
        agent.setMaxTokens(256);

        ModelChatRequest request = new ModelChatRequest();
        request.setProvider(provider);
        request.setAgent(agent);
        request.setMessages(Arrays.asList(
                new ModelChatMessage("system", "You are concise."),
                new ModelChatMessage("user", "hello")
        ));
        return request;
    }

    private AgentTool tool() {
        AgentTool tool = new AgentTool();
        tool.setCode("lookup");
        tool.setName("lookup");
        tool.setDescription("Lookup data");
        tool.setParametersSchema("{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}},\"required\":[\"q\"]}");
        return tool;
    }
}
