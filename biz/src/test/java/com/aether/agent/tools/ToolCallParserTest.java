package com.aether.agent.tools;

import com.aether.agent.model.ModelChatResponse;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolCallParserTest {

    @Test
    void parsesAskUserArgumentsContainingEscapedControlCharacters() {
        JSONObject function = new JSONObject();
        function.put("name", AskUserTool.TOOL_NAME);
        function.put("arguments", "{\"questions\":[{\"id\":\"details\",\"type\":\"choice\","
                + "\"question\":\"请选择\u001a部署环境\",\"options\":[{\"id\":\"dev\","
                + "\"label\":\"开发环境\",\"value\":\"dev\"}]}]}");
        JSONObject toolCall = new JSONObject();
        toolCall.put("id", "call-1");
        toolCall.put("type", "function");
        toolCall.put("function", function);

        ModelChatResponse response = new ModelChatResponse();
        response.setToolCalls(new JSONArray().fluentAdd(toolCall).toJSONString());

        List<ToolCallParser.ToolCall> calls = new ToolCallParser().parse(response);

        assertEquals(1, calls.size());
        assertEquals(AskUserTool.TOOL_NAME, calls.get(0).getName());
        assertEquals("请选择\u001a部署环境", ((List<?>) calls.get(0).getArguments().get("questions"))
                .stream().map(value -> (java.util.Map<?, ?>) value).findFirst().get().get("question"));
    }
}
