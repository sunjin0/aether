package com.aether.agent.tools;

import com.aether.agent.entity.AgentConversation;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.service.AgentConversationService;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.tools.core.Tool;
import com.aether.agent.tools.entity.ToolResult;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Creates a machine-readable human-handoff event and pauses the OpenAPI conversation. */
@Component
public class HumanHandoffTool implements Tool {
    public static final String TOOL_NAME = "human_handoff";
    private final AgentConversationService conversationService;
    private final AgentMessageService messageService;

    public HumanHandoffTool(AgentConversationService conversationService, AgentMessageService messageService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
    }

    @Override
    public AgentTool getTool() {
        AgentTool tool = new AgentTool();
        tool.setId(TOOL_NAME); tool.setCode(TOOL_NAME); tool.setName(TOOL_NAME); tool.setType("internal"); tool.setStatus(1);
        tool.setDescription("当退款争议、投诉或用户明确要求人工时，转接人工客服。仅填写标准原因，不得编造工单号或业务结果。");
        JSONObject schema = new JSONObject(); schema.put("type", "object"); schema.put("additionalProperties", false);
        JSONObject properties = new JSONObject();
        properties.put("reason", new JSONObject().fluentPut("type", "string").fluentPut("maxLength", 64)
                .fluentPut("description", "稳定原因编码，例如 REFUND_DISPUTE、COMPLAINT、USER_REQUEST"));
        properties.put("message", new JSONObject().fluentPut("type", "string").fluentPut("maxLength", 1000)
                .fluentPut("description", "面向客户的简短转接说明"));
        schema.put("properties", properties); schema.put("required", new JSONArray().fluentAdd("reason"));
        tool.setParametersSchema(JSON.toJSONString(schema));
        return tool;
    }

    @Override
    public boolean supports(String toolName) { return TOOL_NAME.equals(toolName); }

    @Override
    public ToolResult handle(String conversationId, Map<String, Object> arguments) {
        String reason = arguments == null ? null : StringUtils.trimToNull(String.valueOf(arguments.get("reason")));
        if (StringUtils.isBlank(reason) || !reason.matches("[A-Z][A-Z0-9_]{1,63}"))
            throw new IllegalArgumentException("HUMAN_HANDOFF reason 必须为稳定大写编码");
        String message = arguments == null ? null : StringUtils.trimToNull(String.valueOf(arguments.get("message")));
        if (message != null && message.length() > 1000) throw new IllegalArgumentException("HUMAN_HANDOFF message 过长");
        AgentConversation conversation = conversationService.getById(conversationId);
        if (conversation == null || Boolean.TRUE.equals(conversation.getDeleted())) throw new IllegalArgumentException("会话不存在");
        AgentConversation update = new AgentConversation();
        update.setId(conversationId); update.setHandoffStatus("HUMAN_HANDLING");
        conversationService.updateById(update);

        JSONObject data = new JSONObject(); data.put("type", "handoff"); data.put("reason", reason);
        AgentMessage event = new AgentMessage();
        event.setConversationId(conversationId); event.setRole("assistant"); event.setMessageType("interaction");
        event.setInteractionType("HUMAN_HANDOFF"); event.setInteractionStatus("pending");
        event.setQuestionConfig(data.toJSONString());
        event.setContent(StringUtils.defaultIfBlank(message, "我已为您转接人工客服。"));
        messageService.save(event);
        return ToolResult.waitingUser(event, "已请求人工转接，原因=" + reason);
    }
}
