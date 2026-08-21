package com.aether.agent.service;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentRunContextMetric;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatMessage;
import com.aether.agent.model.ModelChatResponse;
import com.aether.agent.model.ModelStreamResponse;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Records immutable estimated and provider-reported context metrics per model call. */
@Service
public class ContextMetricService {
    private static final int DEFAULT_CONTEXT_WINDOW_TOKENS = 32768;
    private static final int DEFAULT_OUTPUT_RESERVE_TOKENS = 2048;
    private static final int MIN_INPUT_BUDGET_TOKENS = 256;
    private static final int MESSAGE_BASE_TOKENS = 4;
    private static final Map<String, Double> MODEL_TOKEN_RATIOS;

    private final AgentRunContextMetricService metricService;

    static {
        Map<String, Double> ratios = new HashMap<>();
        ratios.put("gpt-4o", 3.5);
        ratios.put("gpt-4-turbo", 3.5);
        ratios.put("gpt-4", 3.5);
        ratios.put("gpt-3.5-turbo", 3.5);
        ratios.put("claude-3-5", 4.0);
        ratios.put("claude-3", 4.0);
        ratios.put("qwen", 3.0);
        ratios.put("deepseek", 3.5);
        ratios.put("gemma", 3.0);
        ratios.put("llama", 3.0);
        ratios.put("mistral", 3.5);
        MODEL_TOKEN_RATIOS = Collections.unmodifiableMap(ratios);
    }

    public ContextMetricService(AgentRunContextMetricService metricService) {
        this.metricService = metricService;
    }

    public AgentRunContextMetric recordPreliminary(String runId, int attemptNo,
                                                    List<ModelChatMessage> messages,
                                                    AgentDefinition agent, ModelProvider provider) {
        return recordPreliminary(runId, attemptNo, messages, null, agent, provider);
    }

    public AgentRunContextMetric recordPreliminary(String runId, int attemptNo,
                                                    List<ModelChatMessage> messages,
                                                    List<AgentTool> tools,
                                                    AgentDefinition agent, ModelProvider provider) {
        return recordPreliminary(runId, attemptNo, "ANSWER", "NOT_NEEDED",
                messages, tools, agent, provider);
    }

    /**
     * 记录指定模型调用类型的派发前上下文指标。
     */
    public AgentRunContextMetric recordPreliminary(String runId, int attemptNo,
                                                   String callType, String compressionStatus,
                                                   List<ModelChatMessage> messages,
                                                   List<AgentTool> tools,
                                                   AgentDefinition agent, ModelProvider provider) {
        AgentRunContextMetric metric = new AgentRunContextMetric();
        metric.setModelCallId(newId());
        metric.setRunId(runId);
        metric.setCallType(StringUtils.defaultIfBlank(callType, "ANSWER"));
        metric.setAttemptNo(attemptNo);
        metric.setMetricPhase("PRELIMINARY");
        int window = provider != null && provider.getContextWindow() != null && provider.getContextWindow() > 0
                ? provider.getContextWindow() : DEFAULT_CONTEXT_WINDOW_TOKENS;
        int outputReserve = agent != null && agent.getMaxTokens() != null && agent.getMaxTokens() > 0
                ? agent.getMaxTokens() : DEFAULT_OUTPUT_RESERVE_TOKENS;
        int safetyReserve = Math.max(512, window / 20);
        metric.setContextWindowTokens(window);
        metric.setOutputReserveTokens(outputReserve);
        metric.setSafetyReserveTokens(safetyReserve);
        metric.setInputBudgetTokens(getInputTokenBudget(agent, provider));
        metric.setEstimatedPromptTokens(estimateContextTokens(messages,
                agent == null ? null : agent.getModel()));
        metric.setSystemTokens(0).setSkillTokens(0).setTaskTokens(0).setMemoryTokens(0)
                .setSummaryTokens(0).setHistoryTokens(0).setToolTokens(0).setToolDefinitionTokens(0)
                .setRagTokens(0).setCurrentMessageTokens(0).setTrimmedMessageCount(0)
                .setCompressedMessageCount(0)
                .setCompressionStatus(StringUtils.defaultIfBlank(compressionStatus, "NOT_NEEDED"));
        classify(metric, messages, agent == null ? null : agent.getModel());
        metric.setToolDefinitionTokens(estimateToolTokens(tools, agent == null ? null : agent.getModel()));
        metricService.save(metric);
        return metric;
    }

    public AgentRunContextMetric recordFinal(AgentRunContextMetric preliminary, Integer providerPromptTokens) {
        return recordFinal(preliminary, providerPromptTokens, null, null, null);
    }

    public AgentRunContextMetric recordFinal(AgentRunContextMetric preliminary, ModelChatResponse response) {
        return recordFinal(preliminary,
                response == null ? null : response.getPromptTokens(),
                response == null ? null : response.getCachedPromptTokens(),
                response == null ? null : response.getUncachedPromptTokens(),
                response == null ? null : response.getPromptCacheHitRate());
    }

    public AgentRunContextMetric recordFinal(AgentRunContextMetric preliminary, ModelStreamResponse response) {
        return recordFinal(preliminary,
                response == null ? null : response.getPromptTokens(),
                response == null ? null : response.getCachedPromptTokens(),
                response == null ? null : response.getUncachedPromptTokens(),
                response == null ? null : response.getPromptCacheHitRate());
    }

    public AgentRunContextMetric recordFinal(AgentRunContextMetric preliminary, Integer providerPromptTokens,
                                             Integer cachedPromptTokens, Integer uncachedPromptTokens,
                                             Double promptCacheHitRate) {
        return recordFinal(preliminary, providerPromptTokens, cachedPromptTokens, uncachedPromptTokens,
                promptCacheHitRate, null);
    }

    /**
     * 记录指定最终压缩状态的不可变最终指标。
     */
    public AgentRunContextMetric recordFinal(AgentRunContextMetric preliminary, Integer providerPromptTokens,
                                             String compressionStatus) {
        return recordFinal(preliminary, providerPromptTokens, null, null, null, compressionStatus);
    }

    /**
     * 记录指定最终压缩状态和供应商缓存命中观测的不可变最终指标。
     */
    public AgentRunContextMetric recordFinal(AgentRunContextMetric preliminary, Integer providerPromptTokens,
                                             Integer cachedPromptTokens, Integer uncachedPromptTokens,
                                             Double promptCacheHitRate, String compressionStatus) {
        if (preliminary == null) return null;
        AgentRunContextMetric finalMetric = new AgentRunContextMetric();
        org.springframework.beans.BeanUtils.copyProperties(preliminary, finalMetric);
        finalMetric.setModelCallId(newId());
        finalMetric.setSourceModelCallId(preliminary.getModelCallId());
        finalMetric.setMetricPhase("FINAL");
        finalMetric.setPromptTokens(providerPromptTokens);
        applyPromptCacheMetrics(finalMetric, providerPromptTokens, cachedPromptTokens,
                uncachedPromptTokens, promptCacheHitRate);
        if (StringUtils.isNotBlank(compressionStatus)) {
            finalMetric.setCompressionStatus(compressionStatus);
            if ("SYNC_COMPLETED".equals(compressionStatus)) {
                finalMetric.setCompressedMessageCount(1);
            }
        }
        metricService.save(finalMetric);
        return finalMetric;
    }

    private void applyPromptCacheMetrics(AgentRunContextMetric metric, Integer promptTokens,
                                         Integer cachedPromptTokens, Integer uncachedPromptTokens,
                                         Double promptCacheHitRate) {
        if (cachedPromptTokens == null && uncachedPromptTokens == null && promptCacheHitRate == null) {
            return;
        }
        Integer cached = cachedPromptTokens == null ? null : Math.max(0, cachedPromptTokens);
        Integer uncached = uncachedPromptTokens == null ? null : Math.max(0, uncachedPromptTokens);
        if (cached != null && promptTokens != null) {
            cached = Math.min(cached, Math.max(0, promptTokens));
        }
        if (uncached != null && promptTokens != null) {
            uncached = Math.min(uncached, Math.max(0, promptTokens));
        }
        if (cached != null && uncached != null && promptTokens != null && cached + uncached > promptTokens) {
            uncached = Math.max(0, promptTokens - cached);
        }
        if (cached != null && uncached == null && promptTokens != null) {
            uncached = Math.max(0, promptTokens - cached);
        }
        if (cached == null && uncached != null && promptTokens != null) {
            cached = Math.max(0, promptTokens - uncached);
        }
        metric.setCachedPromptTokens(cached);
        metric.setUncachedPromptTokens(uncached);
        if (promptCacheHitRate != null) {
            metric.setPromptCacheHitRate(Math.round(promptCacheHitRate * 100D) / 100D);
        } else if (cached != null && promptTokens != null && promptTokens > 0) {
            metric.setPromptCacheHitRate(Math.round(cached * 10000D / promptTokens) / 100D);
        }
    }

    /**
     * 基于最近一次指定类型的初步指标，写入不可变最终指标。
     */
    public AgentRunContextMetric recordFinalForLatestPreliminary(String runId, String callType,
                                                                 Integer providerPromptTokens,
                                                                 String compressionStatus) {
        if (StringUtils.isBlank(runId)) {
            return null;
        }
        List<AgentRunContextMetric> preliminaries = metricService.list(Wrappers.lambdaQuery(AgentRunContextMetric.class)
                .eq(AgentRunContextMetric::getRunId, runId)
                .eq(AgentRunContextMetric::getCallType, StringUtils.defaultIfBlank(callType, "ANSWER"))
                .eq(AgentRunContextMetric::getMetricPhase, "PRELIMINARY")
                .eq(AgentRunContextMetric::getDeleted, false)
                .orderByDesc(AgentRunContextMetric::getCreatedAt)
                .last("limit 1"));
        if (preliminaries == null || preliminaries.isEmpty()) {
            return null;
        }
        return recordFinal(preliminaries.get(0), providerPromptTokens, compressionStatus);
    }

    /**
     * Estimates the provider-reported cost of the tool definitions sent with this request.
     * The message estimate above never sees these schemas, so they are measured separately
     * to keep the section breakdown additive with the provider total. The wire payload is
     * rebuilt the same way OpenAIModelClient.toJsonTools serializes it (compact JSON,
     * blank-code tools dropped), so the estimate tracks the bytes the model actually sees
     * instead of the pretty-printed schema stored in the database.
     */
    private int estimateToolTokens(List<AgentTool> tools, String model) {
        if (tools == null || tools.isEmpty()) {
            return 0;
        }
        long total = 0;
        for (AgentTool tool : tools) {
            if (tool == null || StringUtils.isBlank(tool.getCode())) {
                continue;
            }
            JSONObject function = new JSONObject();
            function.put("name", tool.getName());
            function.put("description", StringUtils.defaultIfBlank(tool.getDescription(), tool.getCode()));
            if (StringUtils.isNotBlank(tool.getParametersSchema())) {
                function.put("parameters", JSONObject.parseObject(tool.getParametersSchema()));
            } else if (StringUtils.isNotBlank(tool.getMcpInputSchema())) {
                function.put("parameters", JSONObject.parseObject(tool.getMcpInputSchema()));
            } else {
                JSONObject parameters = new JSONObject();
                parameters.put("type", "object");
                parameters.put("properties", new JSONObject());
                parameters.put("additionalProperties", true);
                function.put("parameters", parameters);
            }
            JSONObject toolDefinition = new JSONObject();
            toolDefinition.put("type", "function");
            toolDefinition.put("function", function);
            total += estimateTokens(toolDefinition.toJSONString(), model);
        }
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    /**
     * 返回为本次模型请求预留输出和安全余量后的可用输入 token 数。
     */
    int getInputTokenBudget(AgentDefinition agent, ModelProvider provider) {
        int contextWindow = provider != null && provider.getContextWindow() != null
                && provider.getContextWindow() > 0
                ? provider.getContextWindow() : DEFAULT_CONTEXT_WINDOW_TOKENS;
        int completionReserve = agent != null && agent.getMaxTokens() != null
                && agent.getMaxTokens() > 0
                ? agent.getMaxTokens() : DEFAULT_OUTPUT_RESERVE_TOKENS;
        int safetyReserve = Math.max(512, contextWindow / 20);
        return Math.max(MIN_INPUT_BUDGET_TOKENS,
                contextWindow - completionReserve - safetyReserve);
    }

    /**
     * 估算消息上下文 token 数。
     */
    int estimateContextTokens(List<ModelChatMessage> context, String model) {
        if (context == null || context.isEmpty()) {
            return 0;
        }
        long total = 0;
        for (ModelChatMessage message : context) {
            if (message.getCachedTokens() != null) {
                total += message.getCachedTokens();
                continue;
            }
            int tokens = MESSAGE_BASE_TOKENS;
            tokens += estimateTokens(message.getRole(), model);
            tokens += estimateTokens(message.getContent(), model);
            tokens += estimateTokens(message.getToolCalls(), model);
            tokens += estimateTokens(message.getToolCallId(), model);
            message.setCachedTokens(tokens);
            total += tokens;
        }
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    /**
     * 估算字符串 token 数。
     */
    int estimateTokens(String value, String model) {
        if (StringUtils.isEmpty(value)) {
            return 0;
        }
        return (int) Math.ceil(value.getBytes(StandardCharsets.UTF_8).length / resolveTokenDivisor(model));
    }

    private double resolveTokenDivisor(String model) {
        if (model == null) {
            return 3.0;
        }
        String lowerModel = model.toLowerCase();
        for (Map.Entry<String, Double> entry : MODEL_TOKEN_RATIOS.entrySet()) {
            if (lowerModel.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return 3.0;
    }

    private void classify(AgentRunContextMetric metric, List<ModelChatMessage> messages, String model) {
        if (messages == null) return;
        int finalUser = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).getRole())) { finalUser = i; break; }
        }
        for (int i = 0; i < messages.size(); i++) {
            ModelChatMessage message = messages.get(i);
            int tokens = estimateContextTokens(java.util.Collections.singletonList(message), model);
            String content = StringUtils.defaultString(message.getContent());
            if (content.startsWith("【Skill") || content.startsWith("【技能")) metric.setSkillTokens(metric.getSkillTokens() + tokens);
            else if (content.startsWith("【当前Deep任务】")) metric.setTaskTokens(metric.getTaskTokens() + tokens);
            else if (content.startsWith("【会话记忆】") || content.startsWith("【用户已确认偏好】"))
                metric.setMemoryTokens(metric.getMemoryTokens() + tokens);
            else if (content.startsWith("【运行时上下文】")) metric.setRagTokens(metric.getRagTokens() + tokens);
            else if (content.startsWith("【对话历史摘要】")) metric.setSummaryTokens(metric.getSummaryTokens() + tokens);
            else if ("tool".equals(message.getRole())) metric.setToolTokens(metric.getToolTokens() + tokens);
            else if (i == finalUser) metric.setCurrentMessageTokens(metric.getCurrentMessageTokens() + tokens);
            else if ("user".equals(message.getRole()) || "assistant".equals(message.getRole())) metric.setHistoryTokens(metric.getHistoryTokens() + tokens);
            else if ("system".equals(message.getRole())) {
                metric.setSystemTokens(metric.getSystemTokens() + tokens);
            }
        }
    }

    private String newId() { return UUID.randomUUID().toString().replace("-", ""); }
}
