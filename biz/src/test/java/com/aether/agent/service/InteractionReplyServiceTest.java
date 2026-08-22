package com.aether.agent.service;

import com.aether.agent.entity.AgentMessage;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InteractionReplyServiceTest {

    private final InteractionReplyService service = new InteractionReplyService();

    @Test
    void rendersCancelledConfirmationAsNoForModelContext() {
        AgentMessage question = new AgentMessage();
        question.setQuestionConfig("{\"type\":\"confirm\",\"question\":\"出租人是否拒绝维修？\"}");
        Map<String, Object> answer = new HashMap<>();
        answer.put("confirmed", false);

        assertEquals("用户选择：否（取消）", service.renderAnswerContent(question, answer));
    }

    @Test
    void rendersGroupConfirmationAsYesForModelContext() {
        AgentMessage question = new AgentMessage();
        question.setQuestionConfig("{\"type\":\"group\",\"questions\":[{\"id\":\"registered\",\"type\":\"confirm\",\"question\":\"是否已备案？\"}]} ");
        Map<String, Object> confirmed = new HashMap<>();
        confirmed.put("confirmed", true);
        Map<String, Object> answers = new HashMap<>();
        answers.put("registered", confirmed);

        assertEquals("用户回复：是否已备案？：是（确认）", service.renderAnswerContent(question,
                Collections.<String, Object>singletonMap("answers", answers)));
    }
}
