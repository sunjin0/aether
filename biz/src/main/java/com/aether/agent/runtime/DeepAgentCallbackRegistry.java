package com.aether.agent.runtime;

import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.model.ModelStreamResponse;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.service.AgentStreamCallback;
import com.aether.agent.service.DeepAgentRunService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks live Deep Agent stream callbacks for the current application instance.
 */
@Component
public class DeepAgentCallbackRegistry {
    private final DeepAgentRunService deepAgentRunService;
    private final AgentMessageService agentMessageService;
    private final Map<String, AgentStreamCallback> activeCallbacks = new ConcurrentHashMap<String, AgentStreamCallback>();

    public DeepAgentCallbackRegistry(DeepAgentRunService deepAgentRunService, AgentMessageService agentMessageService) {
        this.deepAgentRunService = deepAgentRunService;
        this.agentMessageService = agentMessageService;
    }

    public void register(String runId, AgentStreamCallback callback) {
        activeCallbacks.put(runId, callback);
    }

    public AgentStreamCallback get(String runId) {
        return activeCallbacks.get(runId);
    }

    public void remove(String runId) {
        activeCallbacks.remove(runId);
    }

    public void reconcileTerminal(String runId) {
        AgentStreamCallback callback = activeCallbacks.get(runId);
        if (callback == null || callback.isClosed()) return;
        AgentRun run = deepAgentRunService.getDeepRunForReconciliation(runId);
        if (Integer.valueOf(0).equals(run.getStatus()) && run.getMessageId() != null) {
            AgentMessage message = agentMessageService.getById(run.getMessageId());
            if (message != null && !Boolean.TRUE.equals(message.getDeleted())) {
                ModelStreamResponse response = new ModelStreamResponse();
                response.setContent(message.getContent());
                response.setReasoningContent(message.getReasoningContent());
                response.setToolCalls(message.getToolCalls());
                response.setModel(message.getModel());
                response.setPromptTokens(message.getPromptTokens());
                response.setCompletionTokens(message.getCompletionTokens());
                response.setTotalTokens(message.getTotalTokens());
                response.setReasoningTokens(message.getReasoningTokens());
                if (message.getCitations() != null)
                    response.setSources(sourceList(JSON.parseObject("{\"sources\":" + message.getCitations() + "}"), "sources"));
                try {
                    callback.onDone(run.getConversationId(), message.getId(), response);
                } finally {
                    remove(runId);
                }
            }
        } else if (Integer.valueOf(1).equals(run.getStatus()) || Integer.valueOf(5).equals(run.getStatus())) {
            notifyErrorAndRemove(runId, callback, Integer.valueOf(5).equals(run.getStatus()) ? 0 : 500,
                    Integer.valueOf(5).equals(run.getStatus()) ? "运行已取消" : run.getErrorMsg());
        }
    }

    public void notifyErrorAndRemove(String runId, AgentStreamCallback callback, int code, String message) {
        if (callback != null) {
            try {
                callback.onError(code, message);
            } finally {
                remove(runId);
            }
        } else {
            remove(runId);
        }
    }

    private List<Map<String, Object>> sourceList(JSONObject data, String key) {
        List<Map> values = data.getList(key, Map.class);
        if (values == null) return null;
        List<Map<String, Object>> sources = new ArrayList<Map<String, Object>>();
        for (Map value : values) {
            Map<String, Object> source = new LinkedHashMap<String, Object>();
            for (Object entryObject : value.entrySet()) {
                Map.Entry entry = (Map.Entry) entryObject;
                source.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            sources.add(source);
        }
        return sources;
    }
}
