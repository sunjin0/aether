package com.aether.agent.service;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.AgentToolCallLog;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatMessage;
import com.aether.agent.service.ConversationSummaryService.SummarySnapshot;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * 组装模型调用所需的会话上下文。
 * 缓存交由 {@link ConversationCacheService}，摘要交由 {@link ConversationSummaryService}。
 */
@Service
public class ConversationContextService {
    private static final int HISTORY_MESSAGE_LIMIT = 20;
    private static final int SUMMARY_TRIGGER_THRESHOLD = 10;
    private static final int KEEP_RECENT_MESSAGES = 5;
    private static final int SUMMARY_BATCH_SIZE = 20;
    private static final int SUMMARY_BATCH_MAX_CHARS = 12000;
    private static final int DEFAULT_CONTEXT_MAX_CHARS = 48000;
    private static final int MIN_OPTIONAL_SYSTEM_CHARS = 512;
    private static final int DEFAULT_CONTEXT_WINDOW_TOKENS = 32768;
    private static final int DEFAULT_COMPLETION_RESERVE_TOKENS = 2048;
    private static final int MIN_INPUT_BUDGET_TOKENS = 256;
    private static final int MIN_OPTIONAL_SYSTEM_TOKENS = 128;
    private static final int MESSAGE_BASE_TOKENS = 4;

    private final AgentMessageService messageService;
    private final ConversationCacheService cacheService;
    private final ConversationSummaryService summaryService;
    private final AgentRunService runService;
    private final AgentToolCallLogService toolCallLogService;

    public ConversationContextService(AgentMessageService messageService,
                                      ConversationCacheService cacheService,
                                      ConversationSummaryService summaryService) {
        this(messageService, cacheService, summaryService, null, null);
    }

    @Autowired
    public ConversationContextService(AgentMessageService messageService,
                                      ConversationCacheService cacheService,
                                      ConversationSummaryService summaryService,
                                      AgentRunService runService,
                                      AgentToolCallLogService toolCallLogService) {
        this.messageService = messageService;
        this.cacheService = cacheService;
        this.summaryService = summaryService;
        this.runService = runService;
        this.toolCallLogService = toolCallLogService;
    }

    /** 查询缓存，缓存未命中时从数据库读取完整的近期上下文。 */
    public List<ModelChatMessage> getOrBuildRecent(AgentDefinition agent, String conversationId) {
        List<ModelChatMessage> cached = cacheService.get(conversationId);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        List<ModelChatMessage> context = buildFromHistory(agent, conversationId);
        cacheService.put(conversationId, context);
        return context;
    }

    /** 追加已持久化的用户、助手或工具交互消息。 */
    public void append(String conversationId, ModelChatMessage message) {
        cacheService.append(conversationId, message);
    }

    /** 从持久化消息构建系统提示与最近 20 条用户/助手消息。 */
    public List<ModelChatMessage> buildFromHistory(AgentDefinition agent, String conversationId) {
        List<ModelChatMessage> context = createSystemContext(agent);
        List<AgentMessage> messages = queryRecentMessages(conversationId, HISTORY_MESSAGE_LIMIT);
        Collections.reverse(messages);
        addMessages(context, messages);
        return context;
    }

    /** 构建摘要与最近消息混合的模型上下文。 */
    public List<ModelChatMessage> buildWithSummary(AgentDefinition agent, ModelProvider provider, String conversationId) {
        List<ModelChatMessage> context = createSystemContext(agent);
        List<ModelChatMessage> cachedMessages = cacheService.get(conversationId);
        long messageCount;
        if (cachedMessages != null && !cachedMessages.isEmpty()) {
            messageCount = cachedMessages.size();
        } else {
            messageCount = countMessages(conversationId);
        }
        if (messageCount <= SUMMARY_TRIGGER_THRESHOLD) {
            if (cachedMessages != null && !cachedMessages.isEmpty()) {
                context.addAll(cachedMessages);
                return context;
            }
            List<AgentMessage> messages = queryRecentMessages(conversationId, SUMMARY_TRIGGER_THRESHOLD);
            Collections.reverse(messages);
            addMessages(context, messages);
            return context;
        }

        SummarySnapshot summary = summaryService.get(conversationId);
        List<AgentMessage> uncoveredMessages;
        if (summary == null) {
            // 首次进入摘要模式时仍使用完整的近期窗口，摘要在后台从最早消息开始连续建立。
            uncoveredMessages = queryRecentMessages(conversationId, HISTORY_MESSAGE_LIMIT);
            Collections.reverse(uncoveredMessages);
            List<AgentMessage> seedBatch = limitSummaryBatch(queryOldestMessages(
                    conversationId, (int) Math.min(SUMMARY_BATCH_SIZE, messageCount - KEEP_RECENT_MESSAGES)));
            summaryService.refreshAsync(conversationId, null, seedBatch, agent, provider);
        } else {
            uncoveredMessages = queryMessagesAfter(conversationId, summary);
            context.add(new ModelChatMessage("system", "【对话历史摘要】" + summary.getSummary()));

            int compressCount = Math.min(
                    SUMMARY_BATCH_SIZE, Math.max(0, uncoveredMessages.size() - KEEP_RECENT_MESSAGES));
            if (compressCount > 0) {
                summaryService.refreshAsync(conversationId, summary,
                        limitSummaryBatch(new ArrayList<AgentMessage>(
                                uncoveredMessages.subList(0, compressCount))), agent, provider);
            }
        }
        addMessages(context, uncoveredMessages);
        return context;
    }

    private List<ModelChatMessage> createSystemContext(AgentDefinition agent) {
        List<ModelChatMessage> context = new ArrayList<ModelChatMessage>();
        if (agent != null && StringUtils.isNotBlank(agent.getSystemPrompt())) {
            context.add(new ModelChatMessage("system", agent.getSystemPrompt()));
        }
        return context;
    }

    private long countMessages(String conversationId) {
        return messageService.count(baseMessageQuery(conversationId));
    }

    private List<AgentMessage> queryRecentMessages(String conversationId, int limit) {
        return messageService.list(baseMessageQuery(conversationId)
                .orderByDesc(AgentMessage::getCreatedAt)
                .orderByDesc(AgentMessage::getId)
                .last("limit " + limit));
    }

    private List<AgentMessage> queryOldestMessages(String conversationId, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        return messageService.list(baseMessageQuery(conversationId)
                .orderByAsc(AgentMessage::getCreatedAt)
                .orderByAsc(AgentMessage::getId)
                .last("limit " + limit));
    }

    private List<AgentMessage> queryMessagesAfter(String conversationId, SummarySnapshot summary) {
        LambdaQueryWrapper<AgentMessage> query = baseMessageQuery(conversationId);
        query.and(wrapper -> wrapper
                .gt(AgentMessage::getCreatedAt, summary.getCoveredUntilCreatedAt())
                .or(nested -> nested
                        .eq(AgentMessage::getCreatedAt, summary.getCoveredUntilCreatedAt())
                        .gt(AgentMessage::getId, summary.getCoveredUntilMessageId())));
        return messageService.list(query
                .orderByAsc(AgentMessage::getCreatedAt)
                .orderByAsc(AgentMessage::getId));
    }

    private LambdaQueryWrapper<AgentMessage> baseMessageQuery(String conversationId) {
        return Wrappers.lambdaQuery(AgentMessage.class)
                .eq(AgentMessage::getConversationId, conversationId)
                .eq(AgentMessage::getDeleted, false)
                .in(AgentMessage::getRole, "user", "assistant");
    }

    private List<AgentMessage> limitSummaryBatch(List<AgentMessage> messages) {
        if (messages.isEmpty()) {
            return messages;
        }
        List<AgentMessage> limited = new ArrayList<AgentMessage>();
        int totalChars = 0;
        for (AgentMessage message : messages) {
            int messageChars = StringUtils.length(message.getContent());
            if (!limited.isEmpty() && totalChars + messageChars > SUMMARY_BATCH_MAX_CHARS) {
                break;
            }
            limited.add(message);
            totalChars += messageChars;
        }
        return limited;
    }

    private void addMessages(List<ModelChatMessage> context, List<AgentMessage> messages) {
        Map<String, List<List<AgentToolCallLog>>> toolHistory = loadToolHistory(messages);
        for (AgentMessage message : messages) {
            List<List<AgentToolCallLog>> runs = toolHistory.get(message.getId());
            if (runs != null) {
                for (List<AgentToolCallLog> logs : runs) {
                    addToolRun(context, logs);
                }
            }
            context.add(new ModelChatMessage(message.getRole(), message.getContent()));
        }
    }

    private Map<String, List<List<AgentToolCallLog>>> loadToolHistory(List<AgentMessage> messages) {
        if (runService == null || toolCallLogService == null || messages.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> messageIds = new ArrayList<String>();
        for (AgentMessage message : messages) {
            if ("assistant".equals(message.getRole()) && StringUtils.isNotBlank(message.getId())) {
                messageIds.add(message.getId());
            }
        }
        if (messageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<AgentRun> runs = runService.list(Wrappers.lambdaQuery(AgentRun.class)
                .in(AgentRun::getMessageId, messageIds)
                .eq(AgentRun::getDeleted, false)
                .orderByAsc(AgentRun::getCreatedAt)
                .orderByAsc(AgentRun::getId));
        if (runs.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> runIds = new ArrayList<String>();
        Map<String, AgentRun> runById = new HashMap<String, AgentRun>();
        for (AgentRun run : runs) {
            runIds.add(run.getId());
            runById.put(run.getId(), run);
        }
        List<AgentToolCallLog> logs = toolCallLogService.list(
                Wrappers.lambdaQuery(AgentToolCallLog.class)
                        .in(AgentToolCallLog::getRunId, runIds)
                        .eq(AgentToolCallLog::getDeleted, false)
                        .ne(AgentToolCallLog::getStatus, 4)
                        .orderByAsc(AgentToolCallLog::getCreatedAt)
                        .orderByAsc(AgentToolCallLog::getId));
        Map<String, List<AgentToolCallLog>> logsByRun =
                new LinkedHashMap<String, List<AgentToolCallLog>>();
        for (AgentToolCallLog log : logs) {
            logsByRun.computeIfAbsent(log.getRunId(), key -> new ArrayList<AgentToolCallLog>())
                    .add(log);
        }
        Map<String, List<List<AgentToolCallLog>>> byMessage =
                new HashMap<String, List<List<AgentToolCallLog>>>();
        for (Map.Entry<String, List<AgentToolCallLog>> entry : logsByRun.entrySet()) {
            AgentRun run = runById.get(entry.getKey());
            if (run != null && StringUtils.isNotBlank(run.getMessageId())) {
                byMessage.computeIfAbsent(run.getMessageId(),
                                key -> new ArrayList<List<AgentToolCallLog>>())
                        .add(entry.getValue());
            }
        }
        return byMessage;
    }

    private void addToolRun(List<ModelChatMessage> context, List<AgentToolCallLog> logs) {
        JSONArray toolCalls = new JSONArray();
        for (AgentToolCallLog log : logs) {
            JSONObject function = new JSONObject();
            function.put("name", log.getToolName());
            function.put("arguments", truncate(
                    StringUtils.defaultIfBlank(log.getArguments(), "{}"), 4000));
            JSONObject call = new JSONObject();
            call.put("id", log.getToolCallId());
            call.put("type", "function");
            call.put("function", function);
            toolCalls.add(call);
        }
        context.add(new ModelChatMessage("assistant", null, toolCalls.toJSONString(), null));
        for (AgentToolCallLog log : logs) {
            String content;
            if (Integer.valueOf(0).equals(log.getStatus())) {
                content = StringUtils.defaultIfBlank(log.getResponseBody(), "工具执行成功，但没有响应体");
            } else {
                content = "工具执行未成功，status=" + log.getStatus()
                        + ", error=" + StringUtils.defaultString(log.getErrorMsg());
            }
            context.add(new ModelChatMessage(
                    "tool", truncate(content, 4000), null, log.getToolCallId()));
        }
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength
                ? value : value.substring(0, maxLength);
    }

    /**
     * 对最终模型上下文执行统一字符预算。优先淘汰最早历史，保留主系统提示、最新用户输入，
     * 并把最近一次 assistant/tool 交互作为不可拆分协议组保留下来。
     */
    public void enforceBudget(List<ModelChatMessage> context) {
        enforceBudget(context, DEFAULT_CONTEXT_MAX_CHARS);
    }

    /** 根据供应商上下文窗口和 Agent 最大输出，计算本次请求可使用的输入 token 预算。 */
    public void enforceBudget(List<ModelChatMessage> context,
                              AgentDefinition agent,
                              ModelProvider provider) {
        int contextWindow = provider != null && provider.getContextWindow() != null
                && provider.getContextWindow() > 0
                ? provider.getContextWindow() : DEFAULT_CONTEXT_WINDOW_TOKENS;
        int completionReserve = agent != null && agent.getMaxTokens() != null
                && agent.getMaxTokens() > 0
                ? agent.getMaxTokens() : DEFAULT_COMPLETION_RESERVE_TOKENS;
        int safetyReserve = Math.max(512, contextWindow / 20);
        int inputBudget = Math.max(MIN_INPUT_BUDGET_TOKENS,
                contextWindow - completionReserve - safetyReserve);
        enforceTokenBudget(context, inputBudget);
    }

    void enforceBudget(List<ModelChatMessage> context, int maxChars) {
        if (context == null || context.isEmpty() || maxChars <= 0) {
            return;
        }
        while (contextChars(context) > maxChars) {
            int candidate = oldestRemovableHistoryIndex(context);
            if (candidate < 0) {
                break;
            }
            removeHistoryGroup(context, candidate);
        }
        trimOptionalSystemMessages(context, maxChars);
        trimProtectedMessages(context, maxChars);
    }

    void enforceTokenBudget(List<ModelChatMessage> context, int maxTokens) {
        if (context == null || context.isEmpty() || maxTokens <= 0) {
            return;
        }
        while (estimateContextTokens(context) > maxTokens) {
            int candidate = oldestRemovableHistoryIndex(context);
            if (candidate < 0) {
                break;
            }
            removeHistoryGroup(context, candidate);
        }
        trimOptionalSystemsToTokenBudget(context, maxTokens);
        trimProtectedContentToTokenBudget(context, maxTokens);
    }

    private static final Map<String, Double> MODEL_TOKEN_RATIOS;

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

    public int estimateTokens(String value) {
        return estimateTokens(value, null);
    }

    public int estimateTokens(String value, String model) {
        if (StringUtils.isEmpty(value)) {
            return 0;
        }
        double divisor = resolveTokenDivisor(model);
        return (int) Math.ceil(value.getBytes(StandardCharsets.UTF_8).length / divisor);
    }

    public int estimateContextTokens(List<ModelChatMessage> context) {
        return estimateContextTokens(context, null);
    }

    public int estimateContextTokens(List<ModelChatMessage> context, String model) {
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

    private void trimOptionalSystemsToTokenBudget(List<ModelChatMessage> context, int maxTokens) {
        while (true) {
            int currentTotal = estimateContextTokens(context);
            if (currentTotal <= maxTokens) {
                return;
            }
            int longestIndex = -1;
            int longestTokens = MIN_OPTIONAL_SYSTEM_TOKENS;
            for (int i = 1; i < context.size(); i++) {
                ModelChatMessage message = context.get(i);
                int tokens = estimateTokens(message.getContent());
                if ("system".equals(message.getRole()) && tokens > longestTokens) {
                    longestIndex = i;
                    longestTokens = tokens;
                }
            }
            if (longestIndex < 0) {
                return;
            }
            int excess = currentTotal - maxTokens;
            int target = Math.max(MIN_OPTIONAL_SYSTEM_TOKENS, longestTokens - excess);
            ModelChatMessage message = context.get(longestIndex);
            message.setContent(abbreviateToTokenBudget(message.getContent(), target));
        }
    }

    private void trimProtectedContentToTokenBudget(List<ModelChatMessage> context, int maxTokens) {
        while (true) {
            int currentTotal = estimateContextTokens(context);
            if (currentTotal <= maxTokens) {
                return;
            }
            int longestIndex = -1;
            int longestTokens = 0;
            for (int i = 0; i < context.size(); i++) {
                int tokens = estimateTokens(context.get(i).getContent());
                if (tokens > longestTokens) {
                    longestIndex = i;
                    longestTokens = tokens;
                }
            }
            if (longestIndex < 0) {
                return;
            }
            int excess = currentTotal - maxTokens;
            int target = Math.max(0, longestTokens - excess);
            ModelChatMessage message = context.get(longestIndex);
            message.setContent(abbreviateToTokenBudget(message.getContent(), target));
        }
    }

    private String abbreviateToTokenBudget(String value, int maxTokens) {
        if (value == null || estimateTokens(value) <= maxTokens) {
            return value;
        }
        int low = 0;
        int high = value.length();
        String best = "";
        while (low <= high) {
            int mid = low + (high - low) / 2;
            String candidate = abbreviate(value, mid);
            if (estimateTokens(candidate) <= maxTokens) {
                best = candidate;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    private int oldestRemovableHistoryIndex(List<ModelChatMessage> context) {
        int latestUser = lastRoleIndex(context, "user");
        int latestToolGroup = lastToolGroupStart(context);
        for (int i = 0; i < context.size(); i++) {
            ModelChatMessage message = context.get(i);
            if ("system".equals(message.getRole()) || i == latestUser
                    || (latestToolGroup >= 0 && i >= latestToolGroup)) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private void removeHistoryGroup(List<ModelChatMessage> context, int index) {
        ModelChatMessage message = context.get(index);
        boolean toolCall = "assistant".equals(message.getRole())
                && StringUtils.isNotBlank(message.getToolCalls());
        context.remove(index);
        if (toolCall) {
            while (index < context.size() && "tool".equals(context.get(index).getRole())) {
                context.remove(index);
            }
        }
    }

    private void trimOptionalSystemMessages(List<ModelChatMessage> context, int maxChars) {
        while (contextChars(context) > maxChars) {
            int longestIndex = -1;
            int longestLength = MIN_OPTIONAL_SYSTEM_CHARS;
            for (int i = 1; i < context.size(); i++) {
                ModelChatMessage message = context.get(i);
                int length = StringUtils.length(message.getContent());
                if ("system".equals(message.getRole()) && length > longestLength) {
                    longestIndex = i;
                    longestLength = length;
                }
            }
            if (longestIndex < 0) {
                return;
            }
            int excess = contextChars(context) - maxChars;
            int target = Math.max(MIN_OPTIONAL_SYSTEM_CHARS, longestLength - excess);
            ModelChatMessage message = context.get(longestIndex);
            message.setContent(abbreviate(message.getContent(), target));
        }
    }

    private void trimProtectedMessages(List<ModelChatMessage> context, int maxChars) {
        while (contextChars(context) > maxChars) {
            int candidate = longestContentIndex(context);
            if (candidate < 0) {
                return;
            }
            ModelChatMessage message = context.get(candidate);
            int length = StringUtils.length(message.getContent());
            int target = Math.max(0, length - (contextChars(context) - maxChars));
            message.setContent(abbreviate(message.getContent(), target));
        }
    }

    private int longestContentIndex(List<ModelChatMessage> context) {
        int index = -1;
        int length = 0;
        for (int i = 0; i < context.size(); i++) {
            int current = StringUtils.length(context.get(i).getContent());
            if (current > length) {
                index = i;
                length = current;
            }
        }
        return index;
    }

    private int lastRoleIndex(List<ModelChatMessage> context, String role) {
        for (int i = context.size() - 1; i >= 0; i--) {
            if (role.equals(context.get(i).getRole())) {
                return i;
            }
        }
        return -1;
    }

    private int lastToolGroupStart(List<ModelChatMessage> context) {
        for (int i = context.size() - 1; i >= 0; i--) {
            ModelChatMessage message = context.get(i);
            if ("assistant".equals(message.getRole())
                    && StringUtils.isNotBlank(message.getToolCalls())) {
                return i;
            }
        }
        return -1;
    }

    private int contextChars(List<ModelChatMessage> context) {
        int total = 0;
        for (ModelChatMessage message : context) {
            total += StringUtils.length(message.getRole());
            total += StringUtils.length(message.getContent());
            total += StringUtils.length(message.getToolCalls());
            total += StringUtils.length(message.getToolCallId());
        }
        return total;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= 0) {
            return "";
        }
        String marker = "\n...[上下文已裁剪]...\n";
        if (maxLength <= marker.length()) {
            return value.substring(0, maxLength);
        }
        int remaining = maxLength - marker.length();
        int head = (remaining * 2) / 3;
        int tail = remaining - head;
        return value.substring(0, head) + marker + value.substring(value.length() - tail);
    }
}
