package com.aether.agent.internal;

import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.tools.AskUserTool;
import com.aether.agent.tools.entity.ToolResult;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证Ask用户Tool的行为。
 */
class AskUserToolTest {

    /**
     * 处理exposesAsk用户DefinitionAndCreatesPendingGroupInteraction。
     */
    @Test
    void exposesAskUserDefinitionAndCreatesPendingGroupInteraction() {
        AgentMessageService messageService = mock(AgentMessageService.class);
        when(messageService.save(any(AgentMessage.class))).thenAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId("message-question-1");
            return true;
        });
        AskUserTool handler = new AskUserTool(messageService);

        AgentTool tool = handler.getTool();
        assertEquals("ask_user", tool.getCode());
        assertEquals("internal", tool.getType());
        assertTrue(tool.getParametersSchema().contains("\"questions\""));

        Map<String, Object> option = new HashMap<>();
        option.put("id", "prod");
        option.put("label", "生产环境");
        option.put("value", "prod");
        Map<String, Object> question = new HashMap<>();
        question.put("id", "environment");
        question.put("type", "choice");
        question.put("question", "请选择部署环境");
        question.put("options", Arrays.asList(option));
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("questions", Arrays.asList(question));

        ToolResult result = handler.handle("conversation-1", arguments);

        assertTrue(result.isWaitingUser());
        assertEquals("message-question-1", result.getMessage().getId());
        assertEquals("interaction", result.getMessage().getMessageType());
        assertEquals("pending", result.getMessage().getInteractionStatus());
        assertEquals("请选择部署环境", result.getMessage().getContent());
        assertEquals("需要用户回复：environment=请选择部署环境", result.getContextContent());
        JSONObject questionConfig = JSONObject.parseObject(result.getMessage().getQuestionConfig());
        JSONObject normalizedQuestion = questionConfig.getJSONArray("questions").getJSONObject(0);
        assertTrue(normalizedQuestion.getBooleanValue("allowCustomInput"));
        assertEquals("请输入自定义内容", normalizedQuestion.getString("customInputPlaceholder"));
    }
}
