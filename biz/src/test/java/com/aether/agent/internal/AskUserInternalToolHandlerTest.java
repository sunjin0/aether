package com.aether.agent.internal;

import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.service.AgentMessageService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AskUserInternalToolHandlerTest {

    @Test
    void exposesAskUserDefinitionAndCreatesPendingGroupInteraction() {
        AgentMessageService messageService = mock(AgentMessageService.class);
        when(messageService.save(any(AgentMessage.class))).thenAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId("message-question-1");
            return true;
        });
        AskUserInternalToolHandler handler = new AskUserInternalToolHandler(messageService);

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

        InternalToolHandleResult result = handler.handle("conversation-1", arguments);

        assertTrue(result.isWaitingUser());
        assertEquals("message-question-1", result.getMessage().getId());
        assertEquals("interaction", result.getMessage().getMessageType());
        assertEquals("pending", result.getMessage().getInteractionStatus());
        assertEquals("请选择部署环境", result.getMessage().getContent());
        assertEquals("需要用户回复：environment=请选择部署环境", result.getContextContent());
    }
}
