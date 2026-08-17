package com.aether.agent.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证OpenAI模型Client的行为。
 */
class OpenAIModelClientTest {

    /**
     * 解析ResponseSupportsReasoningContentWhenContent判断是否为Blank。
     */
    @Test
    void parseResponseSupportsReasoningContentWhenContentIsBlank() throws Exception {
        String responseBody = "{"
                + "\"id\":\"chatcmpl-j489th51ldps2byvi88qi\","
                + "\"object\":\"chat.completion\","
                + "\"created\":1783390544,"
                + "\"model\":\"qwen/qwen3.5-9b\","
                + "\"choices\":[{\"index\":0,\"message\":{"
                + "\"role\":\"assistant\","
                + "\"content\":\"\","
                + "\"reasoning_content\":\"用户反复发送测试，助手提供安全感并引导感官练习。\","
                + "\"tool_calls\":[]"
                + "},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{"
                + "\"prompt_tokens\":0,"
                + "\"completion_tokens\":0,"
                + "\"total_tokens\":0,"
                + "\"completion_tokens_details\":{\"reasoning_tokens\":1896}"
                + "}"
                + "}";

        OpenAIModelClient client = new OpenAIModelClient(new PooledHttpClient());
        Method parseResponse = OpenAIModelClient.class
                .getDeclaredMethod("parseResponse", String.class, String.class);
        parseResponse.setAccessible(true);

        ModelChatResponse result = (ModelChatResponse) parseResponse
                .invoke(client, responseBody, "fallback-model");

        assertEquals("", result.getContent());
        assertEquals("用户反复发送测试，助手提供安全感并引导感官练习。", result.getReasoningContent());
        assertEquals("qwen/qwen3.5-9b", result.getModel());
        assertEquals(0, result.getPromptTokens());
        assertEquals(0, result.getCompletionTokens());
        assertEquals(0, result.getTotalTokens());
        assertEquals(1896, result.getReasoningTokens());
    }
}
