package com.aether.agent.service;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatResponse;
import org.springframework.stereotype.Service;

/** 持久化聊天运行记录及其状态变化。 */
@Service
public class ChatRunService {
    private static final int RUN_STATUS_FAILED = 1;
    private final AgentRunService agentRunService;

    public ChatRunService(AgentRunService agentRunService) {
        this.agentRunService = agentRunService;
    }

    public String create(AgentDefinition agent, ModelProvider provider, String userId, String conversationId,
                         String messageId, String input, ModelChatResponse response, long latencyMs,
                         Integer status, String errorMsg) {
        AgentRun run = new AgentRun();
        run.setAgentDefinitionId(agent.getId());
        run.setConversationId(conversationId);
        run.setMessageId(messageId);
        run.setUserId(userId);
        run.setInputContent(truncate(input));
        run.setOutputContent(response == null ? null : truncate(response.getContent()));
        run.setModel(response == null ? agent.getModel() : response.getModel());
        run.setModelProviderId(provider.getId());
        if (response != null) {
            run.setPromptTokens(response.getPromptTokens());
            run.setCompletionTokens(response.getCompletionTokens());
            run.setTotalTokens(response.getTotalTokens());
        }
        run.setLatencyMs((int) latencyMs);
        run.setStatus(status);
        run.setErrorMsg(truncate(errorMsg));
        agentRunService.save(run);
        return run.getId();
    }

    public void update(String runId, String messageId, ModelChatResponse response, long latencyMs,
                       Integer status, String errorMsg) {
        AgentRun run = new AgentRun();
        run.setId(runId);
        run.setMessageId(messageId);
        run.setOutputContent(response == null ? null : truncate(response.getContent()));
        if (response != null) {
            run.setModel(response.getModel());
            run.setPromptTokens(response.getPromptTokens());
            run.setCompletionTokens(response.getCompletionTokens());
            run.setTotalTokens(response.getTotalTokens());
        }
        run.setLatencyMs((int) latencyMs);
        run.setStatus(status);
        run.setErrorMsg(truncate(errorMsg));
        agentRunService.updateById(run);
    }

    public void saveFailure(AgentDefinition agent, ModelProvider provider, String userId, String conversationId,
                            String messageId, String input, long latencyMs, RuntimeException exception) {
        ModelChatResponse response = new ModelChatResponse();
        response.setModel(agent.getModel());
        create(agent, provider, userId, conversationId, messageId, input, response,
                latencyMs, RUN_STATUS_FAILED, exception.getMessage());
    }

    private String truncate(String value) {
        return value == null || value.length() <= 1024 ? value : value.substring(0, 1024);
    }
}
