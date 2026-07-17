/*
 * Copyright (c) 2026. Lorem ipsum dolor sit amet, consectetur adipiscing elit.
 * Morbi non lorem porttitor neque feugiat blandit. Ut vitae ipsum eget quam lacinia accumsan.
 * Etiam sed turpis ac ipsum condimentum fringilla. Maecenas magna.
 * Proin dapibus sapien vel ante. Aliquam erat volutpat. Pellentesque sagittis ligula eget metus.
 * Vestibulum commodo. Ut rhoncus gravida arcu.
 */

package com.aether.agent.tools;

import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.tools.core.Tool;
import com.aether.agent.tools.entity.ToolResult;
import com.aether.exception.ServerException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将模型的 ask_user 调用持久化为平台交互消息，并暂停当前轮次。
 */
@Component
public class AskUserTool implements Tool {

    public static final String TOOL_NAME = "ask_user";
    private static final String INTERACTION_STATUS_PENDING = "pending";
    private final AgentMessageService agentMessageService;

    public AskUserTool(AgentMessageService agentMessageService) {
        this.agentMessageService = agentMessageService;
    }

    @Override
    public AgentTool getTool() {
        AgentTool tool = new AgentTool();
        tool.setId(TOOL_NAME);
        tool.setCode(TOOL_NAME);
        tool.setName(TOOL_NAME);
        tool.setDescription("向用户提出1-4个结构化问题。支持choice（选择）和confirm（确认）类型。一次调用可提出多个相关问题，前端以Tab页签展示。每个问题必须有唯一的id（snake_case）用于匹配答案。只有在需要用户输入时才使用此工具，禁止用普通文本输出需要用户回答的问题。");
        tool.setType("internal");
        tool.setParametersSchema(buildParametersSchema());
        tool.setStatus(1);
        return tool;
    }

    @Override
    public boolean supports(String toolName) {
        return TOOL_NAME.equals(toolName);
    }

    @Override
    public ToolResult handle(String conversationId, Map<String, Object> arguments) {
        List<JSONObject> questions = normalizeQuestions(arguments);
        JSONObject groupConfig = new JSONObject();
        groupConfig.put("type", "group");
        groupConfig.put("layout", "tabs");
        groupConfig.put("question", buildGroupTitle(arguments, questions));
        groupConfig.put("questions", questions);

        AgentMessage message = new AgentMessage();
        message.setConversationId(conversationId);
        message.setRole("assistant");
        message.setMessageType("interaction");
        message.setInteractionType("group");
        message.setInteractionStatus(INTERACTION_STATUS_PENDING);
        message.setQuestionConfig(groupConfig.toJSONString());
        message.setContent(groupConfig.getString("question"));
        agentMessageService.save(message);
        return ToolResult.waitingUser(message, buildContextContent(questions));
    }

    private List<JSONObject> normalizeQuestions(Map<String, Object> arguments) {
        if (arguments == null || !(arguments.get("questions") instanceof List)) {
            throw new ServerException(400, "ask_user必须包含questions数组");
        }
        List<?> inputQuestions = (List<?>) arguments.get("questions");
        if (inputQuestions.isEmpty() || inputQuestions.size() > 4) {
            throw new ServerException(400, "ask_user questions数量必须为1-4个");
        }
        Set<String> ids = new HashSet<>();
        List<JSONObject> questions = new ArrayList<>();
        for (Object item : inputQuestions) {
            if (!(item instanceof Map)) {
                throw new ServerException(400, "ask_user问题格式不合法");
            }
            @SuppressWarnings("unchecked")
            JSONObject question = normalizeQuestion((Map<String, Object>) item);
            if (!ids.add(question.getString("id"))) {
                throw new ServerException(400, "ask_user问题id重复: " + question.getString("id"));
            }
            questions.add(question);
        }
        return questions;
    }

    private JSONObject normalizeQuestion(Map<String, Object> inputMap) {
        JSONObject input = new JSONObject(inputMap);
        String id = StringUtils.defaultString(input.getString("id")).trim();
        String type = normalizeType(input.getString("type"), input);
        String question = input.getString("question");
        if (StringUtils.isBlank(id) || id.length() > 64) {
            throw new ServerException(400, "ask_user问题id不能为空且不能超过64字符");
        }
        if (!"choice".equals(type) && !"confirm".equals(type)) {
            throw new ServerException(400, "ask_user.type必须为choice/confirm");
        }
        if (StringUtils.isBlank(question) || question.length() > 1000) {
            throw new ServerException(400, "ask_user.question不能为空且不能超过1000字符");
        }
        JSONObject normalized = new JSONObject();
        normalized.put("id", id);
        normalized.put("type", type);
        normalized.put("question", question);
        if ("choice".equals(type)) {
            JSONArray options = input.getJSONArray("options");
            if (options == null || options.isEmpty() || options.size() > 5) {
                throw new ServerException(400, "choice提问必须包含1-5个选项");
            }
            JSONArray normalizedOptions = new JSONArray();
            for (int i = 0; i < options.size(); i++) {
                JSONObject option = options.getJSONObject(i);
                String optionId = option.getString("id");
                String label = option.getString("label");
                String value = option.getString("value");
                if (StringUtils.isAnyBlank(optionId, label, value) || optionId.length() > 64 || label.length() > 200 || value.length() > 200) {
                    throw new ServerException(400, "choice选项字段不合法");
                }
                normalizedOptions.add(new JSONObject().fluentPut("id", optionId).fluentPut("label", label).fluentPut("value", value));
            }
            normalized.put("options", normalizedOptions);
            normalized.put("multiple", Boolean.TRUE.equals(input.getBoolean("multiple")));
            // Choice questions are rendered as a select control with a companion
            // text box.  A user can therefore provide a value that is not among
            // the model-provided options.
            normalized.put("allowCustomInput", !Boolean.FALSE.equals(input.getBoolean("allowCustomInput")));
            normalized.put("customInputPlaceholder", StringUtils.defaultIfBlank(
                    truncate(input.getString("customInputPlaceholder"), 200), "请输入自定义内容"));
        } else {
            normalized.put("confirmText", StringUtils.defaultIfBlank(truncate(input.getString("confirmText"), 100), "确认"));
            normalized.put("cancelText", StringUtils.defaultIfBlank(truncate(input.getString("cancelText"), 100), "取消"));
        }
        return normalized;
    }

    private String normalizeType(String rawType, JSONObject input) {
        String type = StringUtils.defaultString(rawType).trim().toLowerCase();
        if ("choice".equals(type) || "choices".equals(type) || "select".equals(type) || "option".equals(type)
                || "options".equals(type) || "single_choice".equals(type) || "multiple_choice".equals(type)
                || "radio".equals(type) || "checkbox".equals(type) || "选择".equals(type) || "单选".equals(type) || "多选".equals(type)) {
            return "choice";
        }
        if ("confirm".equals(type) || "confirmation".equals(type) || "yes_no".equals(type) || "yesno".equals(type)
                || "boolean".equals(type) || "bool".equals(type) || "approve".equals(type) || "approval".equals(type)
                || "确认".equals(type) || "是否".equals(type)) {
            return "confirm";
        }
        return StringUtils.isBlank(type) && input.getJSONArray("options") != null ? "choice" : type;
    }

    private String buildGroupTitle(Map<String, Object> arguments, List<JSONObject> questions) {
        Object title = arguments.get("question") == null ? arguments.get("title") : arguments.get("question");
        if (title != null && StringUtils.isNotBlank(title.toString())) {
            return truncate(title.toString(), 1000);
        }
        return questions.size() == 1 ? questions.get(0).getString("question") : "请确认以下 " + questions.size() + " 个问题后继续。";
    }

    private String buildContextContent(List<JSONObject> questions) {
        List<String> items = new ArrayList<>();
        for (JSONObject question : questions) {
            items.add(question.getString("id") + "=" + question.getString("question"));
        }
        return "需要用户回复：" + StringUtils.join(items, "；");
    }

    private String buildParametersSchema() {
        JSONObject optionProperties = new JSONObject();
        optionProperties.put("id", new JSONObject().fluentPut("type", "string").fluentPut("maxLength", 64));
        optionProperties.put("label", new JSONObject().fluentPut("type", "string").fluentPut("maxLength", 200));
        optionProperties.put("value", new JSONObject().fluentPut("type", "string").fluentPut("maxLength", 200));
        JSONObject option = new JSONObject();
        option.put("type", "object");
        option.put("properties", optionProperties);
        option.put("required", new JSONArray().fluentAdd("id").fluentAdd("label").fluentAdd("value"));
        option.put("additionalProperties", false);

        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        schema.put("required", new JSONArray().fluentAdd("questions"));
        schema.put("additionalProperties", false);
        JSONObject question = new JSONObject();
        question.put("type", "object");
        question.put("required", new JSONArray().fluentAdd("id").fluentAdd("type").fluentAdd("question"));
        question.put("additionalProperties", false);
        JSONObject properties = new JSONObject();
        properties.put("id", new JSONObject().fluentPut("type", "string").fluentPut("maxLength", 64));
        properties.put("type", new JSONObject().fluentPut("type", "string").fluentPut("enum", new JSONArray().fluentAdd("choice").fluentAdd("confirm")));
        properties.put("question", new JSONObject().fluentPut("type", "string").fluentPut("maxLength", 1000));
        properties.put("options", new JSONObject().fluentPut("type", "array").fluentPut("maxItems", 5).fluentPut("items", option));
        properties.put("multiple", new JSONObject().fluentPut("type", "boolean"));
        properties.put("allowCustomInput", new JSONObject().fluentPut("type", "boolean")
                .fluentPut("description", "choice 是否显示自定义输入框；默认 true"));
        properties.put("customInputPlaceholder", new JSONObject().fluentPut("type", "string").fluentPut("maxLength", 200)
                .fluentPut("description", "自定义输入框的占位提示"));
        properties.put("confirmText", new JSONObject().fluentPut("type", "string").fluentPut("maxLength", 100));
        properties.put("cancelText", new JSONObject().fluentPut("type", "string").fluentPut("maxLength", 100));
        question.put("properties", properties);
        schema.put("properties", new JSONObject().fluentPut("questions", new JSONObject().fluentPut("type", "array").fluentPut("minItems", 1).fluentPut("maxItems", 4).fluentPut("items", question)));
        return JSON.toJSONString(schema);
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
