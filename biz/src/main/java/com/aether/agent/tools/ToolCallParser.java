package com.aether.agent.tools;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aether.agent.model.ModelChatResponse;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 将模型供应商返回的 tool-call JSON 统一转换为平台内部对象。
 *
 * <p>不同供应商的上游响应可能不完整或格式错误，因此解析失败时返回空列表，
 * 由聊天流程按普通回复继续处理，避免单次响应导致整个会话失败。</p>
 */
@Component
public class ToolCallParser {

    private static final Logger log = LoggerFactory.getLogger(ToolCallParser.class);

    public List<ToolCall> parse(ModelChatResponse response) {
        List<ToolCall> calls = new ArrayList<>();
        if (response == null || StringUtils.isBlank(response.getToolCalls())) {
            return calls;
        }
        try {
            JSONArray array = JSONArray.parseArray(response.getToolCalls());
            if (array == null) {
                return calls;
            }
            for (int i = 0; i < array.size(); i++) {
                JSONObject item = array.getJSONObject(i);
                JSONObject function = item.getJSONObject("function");
                if (function == null) {
                    continue;
                }
                String argumentsJson = function.getString("arguments");
                Map<String, Object> arguments = StringUtils.isBlank(argumentsJson)
                        ? new HashMap<String, Object>() : JSON.parseObject(argumentsJson, Map.class);
                calls.add(new ToolCall(item.getString("id"), function.getString("name"), arguments));
            }
        } catch (Exception e) {
            log.error("解析模型工具调用失败，将忽略本次工具调用", e);
        }
        return calls;
    }

    @Getter
    public static class ToolCall {
        private final String id;
        private final String name;
        private final Map<String, Object> arguments;

        public ToolCall(String id, String name, Map<String, Object> arguments) {
            this.id = id;
            this.name = name;
            this.arguments = arguments;
        }
    }
}
