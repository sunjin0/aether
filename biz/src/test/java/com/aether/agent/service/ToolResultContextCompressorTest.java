package com.aether.agent.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 工具结果上下文压缩的单元测试。 */
class ToolResultContextCompressorTest {

    @Test
    void searchResultKeepsTotalAndBoundedSample() {
        JSONArray items = new JSONArray();
        for (int i = 0; i < 12; i++) {
            JSONObject item = new JSONObject();
            item.put("title", "结果" + i);
            item.put("content", repeat('x', 1400));
            items.add(item);
        }
        String result = new ToolResultContextCompressor().compact("search", items.toJSONString());
        assertTrue(result.contains("\"total\":12"));
        assertTrue(result.contains("高相关样本"));
        assertTrue(result.length() <= 6000);
    }

    @Test
    void diagnosticFieldsAreRemovedFromObjectResult() {
        JSONObject source = new JSONObject();
        source.put("data", repeat('d', 6500));
        source.put("trace", repeat('t', 100));
        String result = new ToolResultContextCompressor().compact("analytics", source.toJSONString());
        assertTrue(!result.contains("trace"));
        assertTrue(result.length() <= 6000);
    }

    private String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) builder.append(value);
        return builder.toString();
    }
}
