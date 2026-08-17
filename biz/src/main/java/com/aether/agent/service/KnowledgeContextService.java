package com.aether.agent.service;

import com.aether.agent.model.ModelChatMessage;
import com.aether.agent.model.ModelChatResponse;
import com.aether.agent.model.ModelStreamResponse;
import com.aether.knowledge.model.KnowledgeRetrievalResult;
import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import com.aether.knowledge.entity.KnowledgeReferenceLog;
import com.aether.knowledge.entity.KnowledgeRetrievalLog;
import com.aether.knowledge.mapper.KnowledgeReferenceLogMapper;
import com.aether.knowledge.mapper.KnowledgeRetrievalLogMapper;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeRetrievalService;
import com.aether.agent.observability.ChatLatencyMetrics;
import com.aether.sys.service.AdminPreferenceService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;

/**
 * 负责把用户偏好和知识库检索结果注入模型上下文，并处理回答中的知识库引用。
 */
@Service
public class KnowledgeContextService {
    private static final Pattern CITATION_PATTERN = Pattern.compile("【(\\d+)】");
    private static final Pattern CASUAL_QUERY_PATTERN = Pattern.compile(
            "(?i)^(hi|hello|hey|ok|okay|thanks|thank you|你好|您好|嗨|哈喽|谢谢|感谢|好的|行|明白|收到)[!！,.，。?？ ]*$");

    private final AdminPreferenceService preferenceService;
    private final KnowledgeRetrievalService retrievalService;
    private final KnowledgeDocumentService documentService;
    private final KnowledgeReferenceLogMapper referenceLogMapper;
    private final KnowledgeRetrievalLogMapper retrievalLogMapper;
    /**
     * Audit writes must not delay a streamed answer. The bounded queue protects request workers under DB pressure.
     */
    private final ThreadPoolExecutor auditExecutor = new ThreadPoolExecutor(1, 2, 30L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(500), new ThreadPoolExecutor.AbortPolicy());

    /**
     * 创建 {@code KnowledgeContextService} 实例。
     */
    @Autowired
    public KnowledgeContextService(AdminPreferenceService preferenceService,
                                   KnowledgeRetrievalService retrievalService,
                                   KnowledgeDocumentService documentService,
                                   KnowledgeReferenceLogMapper referenceLogMapper,
                                   KnowledgeRetrievalLogMapper retrievalLogMapper) {
        this.preferenceService = preferenceService;
        this.retrievalService = retrievalService;
        this.documentService = documentService;
        this.referenceLogMapper = referenceLogMapper;
        this.retrievalLogMapper = retrievalLogMapper;
    }

    /**
     * 兼容不需要持久化引用记录的轻量调用方和单元测试。
     */
    public KnowledgeContextService(AdminPreferenceService preferenceService,
                                   KnowledgeRetrievalService retrievalService,
                                   KnowledgeDocumentService documentService) {
        this(preferenceService, retrievalService, documentService, null, null);
    }

    /**
     * 创建 {@code KnowledgeContextService} 实例。
     */
    public KnowledgeContextService(AdminPreferenceService preferenceService,
                                   KnowledgeRetrievalService retrievalService,
                                   KnowledgeDocumentService documentService,
                                   KnowledgeReferenceLogMapper referenceLogMapper) {
        this(preferenceService, retrievalService, documentService, referenceLogMapper, null);
    }

    /**
     * 将用户偏好、检索文本和可引用来源注入模型上下文，并返回来源供 SSE 最终事件使用。
     */
    public List<Map<String, Object>> enhance(List<ModelChatMessage> context, String userId,
                                             String conversationId, String agentId, String query) {
        List<Map<String, Object>> sources = new ArrayList<>();
        if (context == null) {
            return sources;
        }
        int insertIndex = 0;
        while (insertIndex < context.size() && "system".equals(context.get(insertIndex).getRole())) {
            insertIndex++;
        }
        long preferenceStartedAt = System.currentTimeMillis();
        String preferenceContext = preferenceService.buildPreferenceContext(userId, null, conversationId);
        ChatLatencyMetrics.record("chat.preference_context", System.currentTimeMillis() - preferenceStartedAt);
        // Preferences affect presentation only; do not send them to retrieval/history
        // reformulation where they add tokens but cannot improve source selection.
        KnowledgeRetrievalResult retrieval = retrievalService.retrieveWithHistory(agentId, query, context);
        if (retrieval == null) {
            retrieval = new KnowledgeRetrievalResult();
        }
        List<KnowledgeDocumentChunk> chunks = retrieval.getChunks() == null
                ? Collections.<KnowledgeDocumentChunk>emptyList() : retrieval.getChunks();
        Map<String, String> documentNames = resolveDocumentNames(chunks);
        for (KnowledgeDocumentChunk chunk : chunks) {
            Map<String, Object> source = new HashMap<>();
            source.put("knowledgeBaseId", chunk.getKnowledgeBaseId());
            source.put("documentId", chunk.getDocumentId());
            source.put("documentVersionId", chunk.getDocumentVersionId());
            String documentName = documentNames.get(chunk.getDocumentId());
            source.put("documentName", StringUtils.defaultIfBlank(documentName,
                    "知识库文档 " + (sources.size() + 1)));
            source.put("citationIndex", sources.size() + 1);
            source.put("chunkId", chunk.getId());
            source.put("chunkIndex", chunk.getChunkIndex());
            source.put("sectionPath", chunk.getSectionPath());
            source.put("similarity", chunk.getSimilarity());
            source.put("retrievalScore", chunk.getRetrievalScore());
            source.put("contextExpanded", Boolean.TRUE.equals(chunk.getContextExpanded()));
            source.put("contextChunkCount", chunk.getContextChunkCount());
            source.put("content", truncate(chunk.getContent(), 500));
            sources.add(source);
        }
        appendRuntimeContext(context, insertIndex, preferenceContext, retrieval, sources);
        return sources;
    }

    /**
     * 按 Skill 冻结后的知识库集合检索，避免平台知识库在受限运行中被自动加入。
     */
    public List<Map<String, Object>> enhance(List<ModelChatMessage> context, String userId, String conversationId,
                                             String agentId, String query, Set<String> knowledgeBaseIds) {
        return enhanceScoped(context, userId, conversationId, agentId, query, knowledgeBaseIds);
    }

    /**
     * 处理enhanceScoped。
     */
    private List<Map<String, Object>> enhanceScoped(List<ModelChatMessage> context, String userId, String conversationId,
                                                    String agentId, String query, Set<String> knowledgeBaseIds) {
        List<Map<String, Object>> sources = new ArrayList<>();
        if (context == null) return sources;
        int insertIndex = 0;
        while (insertIndex < context.size() && "system".equals(context.get(insertIndex).getRole())) insertIndex++;
        long preferenceStartedAt = System.currentTimeMillis();
        String preferenceContext = preferenceService.buildPreferenceContext(userId, null, conversationId);
        ChatLatencyMetrics.record("chat.preference_context", System.currentTimeMillis() - preferenceStartedAt);
        KnowledgeRetrievalResult retrieval = shouldSkipRetrieval(query, knowledgeBaseIds)
                ? new KnowledgeRetrievalResult() : retrievalService.retrieve(agentId, query, knowledgeBaseIds);
        if (retrieval == null) retrieval = new KnowledgeRetrievalResult();
        List<KnowledgeDocumentChunk> chunks = retrieval.getChunks() == null ? Collections.<KnowledgeDocumentChunk>emptyList() : retrieval.getChunks();
        Map<String, String> documentNames = resolveDocumentNames(chunks);
        for (KnowledgeDocumentChunk chunk : chunks) {
            Map<String, Object> source = new HashMap<>();
            source.put("knowledgeBaseId", chunk.getKnowledgeBaseId());
            source.put("documentId", chunk.getDocumentId());
            source.put("documentVersionId", chunk.getDocumentVersionId());
            source.put("documentName", StringUtils.defaultIfBlank(documentNames.get(chunk.getDocumentId()), "知识库文档 " + (sources.size() + 1)));
            source.put("citationIndex", sources.size() + 1);
            source.put("chunkId", chunk.getId());
            source.put("chunkIndex", chunk.getChunkIndex());
            source.put("sectionPath", chunk.getSectionPath());
            source.put("similarity", chunk.getSimilarity());
            source.put("retrievalScore", chunk.getRetrievalScore());
            source.put("content", truncate(chunk.getContent(), 500));
            sources.add(source);
        }
        appendRuntimeContext(context, insertIndex, preferenceContext, retrieval, sources);
        return sources;
    }

    /**
     * Keeps request-specific guidance together without merging it into protected Skill rules.
     */
    private void appendRuntimeContext(List<ModelChatMessage> context, int insertIndex, String preferenceContext,
                                      KnowledgeRetrievalResult retrieval, List<Map<String, Object>> sources) {
        StringBuilder runtime = new StringBuilder();
        appendSection(runtime, preferenceContext);
        if (StringUtils.isNotBlank(retrieval.getContext())) {
            appendSection(runtime, retrieval.getContext());
        } else if (retrieval.isRetrievalAttempted()) {
            appendSection(runtime, retrieval.isStrictGrounding()
                    ? "本轮未检索到足以支撑回答的知识库片段。当前 Agent 只能基于知识库资料回答；"
                      + "请明确说明资料不足，并请求用户补充资料或换一种表述，不得使用模型固有知识作答。"
                    : "本轮未检索到足以支撑回答的知识库片段。不得将推测或模型固有知识表述为知识库结论；"
                      + "如果用户要求依据知识库回答，请明确说明当前资料不足，并在必要时请求补充信息。");
        }
        if (sources != null && !sources.isEmpty()) {
            appendSection(runtime, buildCitationInstruction(sources, retrieval.isStrictGrounding()));
        }
        if (runtime.length() == 0) return;
        context.add(insertIndex, new ModelChatMessage("system", "【运行时上下文】\n" + runtime));
    }

    /**
     * 处理appendSection。
     */
    private void appendSection(StringBuilder target, String section) {
        if (StringUtils.isBlank(section)) return;
        if (target.length() > 0) target.append("\n\n");
        target.append(section.trim());
    }

    /**
     * Skip only unambiguous casual turns for non-Skill chats; scoped Skill knowledge is always retrieved.
     */
    private boolean shouldSkipRetrieval(String query, Set<String> knowledgeBaseIds) {
        return knowledgeBaseIds == null && StringUtils.isNotBlank(query)
                && CASUAL_QUERY_PATTERN.matcher(query.trim()).matches();
    }

    /**
     * Removes citations that do not belong to this retrieval before the answer reaches the client.
     */
    public List<Map<String, Object>> ensureCitations(ModelStreamResponse response, List<Map<String, Object>> sources) {
        if (response == null) return Collections.emptyList();
        response.setContent(removeUnknownCitations(response.getContent(), sources));
        return filterCitedSources(response.getContent(), sources);
    }

    /**
     * Non-streaming version of citation sanitization.
     */
    public List<Map<String, Object>> ensureCitations(ModelChatResponse response, List<Map<String, Object>> sources) {
        if (response == null) return Collections.emptyList();
        response.setContent(removeUnknownCitations(response.getContent(), sources));
        return filterCitedSources(response.getContent(), sources);
    }

    /**
     * Persists only sources explicitly cited by the model. Citation auditing must not make
     * a successful chat fail, so persistence errors are deliberately isolated from the reply path.
     */
    public void recordCitations(String agentId, String conversationId, String messageId,
                                List<Map<String, Object>> citedSources) {
        if (referenceLogMapper == null || citedSources == null || citedSources.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        try {
            for (Map<String, Object> source : citedSources) {
                String chunkId = stringValue(source.get("chunkId"));
                String knowledgeBaseId = stringValue(source.get("knowledgeBaseId"));
                String documentId = stringValue(source.get("documentId"));
                if (StringUtils.isBlank(chunkId) || StringUtils.isBlank(knowledgeBaseId)
                        || StringUtils.isBlank(documentId)) {
                    continue;
                }
                KnowledgeReferenceLog log = new KnowledgeReferenceLog();
                log.setAgentDefinitionId(agentId);
                log.setConversationId(conversationId);
                log.setMessageId(messageId);
                log.setKnowledgeBaseId(knowledgeBaseId);
                log.setDocumentId(documentId);
                log.setDocumentVersionId(stringValue(source.get("documentVersionId")));
                log.setChunkId(chunkId);
                log.setSimilarity(doubleValue(source.get("similarity")));
                log.setCitationNo(integerValue(source.get("citationIndex")));
                log.setReferencedAt(now);
                if (referenceLogMapper.insert(log) > 0) {
                    referenceLogMapper.incrementChunkReference(chunkId, now);
                    referenceLogMapper.incrementDocumentReference(documentId, now);
                    referenceLogMapper.incrementKnowledgeBaseReference(knowledgeBaseId, now);
                }
            }
        } catch (Exception ignored) {
            // Citation logs are observability data; the user-visible answer is already persisted.
        }
    }

    /**
     * Queues citation audit persistence after the user-visible response has been finalized.
     */
    public void recordCitationsAsync(String agentId, String conversationId, String messageId,
                                     List<Map<String, Object>> citedSources) {
        submitAudit("citations", () -> recordCitations(agentId, conversationId, messageId, citedSources));
    }

    /**
     * Records candidate-level retrieval outcomes without persisting raw user queries.
     */
    public void recordRetrievalOutcome(String agentId, String conversationId, String messageId, String query,
                                       List<Map<String, Object>> retrievedSources,
                                       List<Map<String, Object>> citedSources) {
        if (retrievalLogMapper == null) {
            return;
        }
        long now = System.currentTimeMillis();
        String queryHash = hashQuery(query);
        Set<String> citedChunkIds = new HashSet<>();
        if (citedSources != null) {
            for (Map<String, Object> source : citedSources) {
                String chunkId = stringValue(source.get("chunkId"));
                if (StringUtils.isNotBlank(chunkId)) {
                    citedChunkIds.add(chunkId);
                }
            }
        }
        try {
            if (retrievedSources == null || retrievedSources.isEmpty()) {
                KnowledgeRetrievalLog log = new KnowledgeRetrievalLog();
                log.setAgentDefinitionId(agentId);
                log.setConversationId(conversationId);
                log.setMessageId(messageId);
                log.setQueryHash(queryHash);
                log.setCited(false);
                log.setOutcome("NO_MATCH");
                log.setRetrievedAt(now);
                retrievalLogMapper.insert(log);
                return;
            }
            for (Map<String, Object> source : retrievedSources) {
                KnowledgeRetrievalLog log = new KnowledgeRetrievalLog();
                String chunkId = stringValue(source.get("chunkId"));
                log.setAgentDefinitionId(agentId);
                log.setConversationId(conversationId);
                log.setMessageId(messageId);
                log.setQueryHash(queryHash);
                log.setKnowledgeBaseId(stringValue(source.get("knowledgeBaseId")));
                log.setDocumentId(stringValue(source.get("documentId")));
                log.setChunkId(chunkId);
                log.setSimilarity(doubleValue(source.get("similarity")));
                log.setRetrievalScore(doubleValue(source.get("retrievalScore")));
                log.setCited(StringUtils.isNotBlank(chunkId) && citedChunkIds.contains(chunkId));
                log.setOutcome("MATCHED");
                log.setRetrievedAt(now);
                retrievalLogMapper.insert(log);
            }
        } catch (Exception ignored) {
            // Observability failures must not affect a successful response.
        }
    }

    /**
     * Queues retrieval audit persistence after the user-visible response has been finalized.
     */
    public void recordRetrievalOutcomeAsync(String agentId, String conversationId, String messageId, String query,
                                            List<Map<String, Object>> retrievedSources,
                                            List<Map<String, Object>> citedSources) {
        submitAudit("retrieval", () -> recordRetrievalOutcome(agentId, conversationId, messageId, query,
                retrievedSources, citedSources));
    }

    /**
     * 提交Audit。
     */
    private void submitAudit(String auditType, Runnable task) {
        try {
            auditExecutor.execute(task);
        } catch (RejectedExecutionException e) {
            // Audit data is best-effort; dropping it is preferable to delaying a live chat response.
            org.slf4j.LoggerFactory.getLogger(KnowledgeContextService.class)
                    .warn("知识审计任务被拒绝: type={}, active={}, queued={}", auditType,
                            auditExecutor.getActiveCount(), auditExecutor.getQueue().size());
        }
    }

    /**
     * 处理shutdownAuditExecutor。
     */
    @PreDestroy
    public void shutdownAuditExecutor() {
        auditExecutor.shutdown();
    }

    /**
     * 解析文档Names。
     */
    private Map<String, String> resolveDocumentNames(List<KnowledgeDocumentChunk> chunks) {
        List<String> documentIds = new ArrayList<>();
        for (KnowledgeDocumentChunk chunk : chunks) {
            if (StringUtils.isNotBlank(chunk.getDocumentId()) && !documentIds.contains(chunk.getDocumentId())) {
                documentIds.add(chunk.getDocumentId());
            }
        }
        Map<String, String> names = new HashMap<>();
        if (!documentIds.isEmpty()) {
            for (KnowledgeDocument document : documentService.listByIds(documentIds)) {
                names.put(document.getId(), StringUtils.defaultIfBlank(document.getTitle(),
                        document.getOriginalFileName()));
            }
        }
        return names;
    }

    /**
     * 处理filterCitedSources。
     */
    private List<Map<String, Object>> filterCitedSources(String content, List<Map<String, Object>> sources) {
        if (StringUtils.isBlank(content) || sources == null || sources.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Integer> citedIndexes = new HashSet<>();
        Matcher matcher = CITATION_PATTERN.matcher(content);
        while (matcher.find()) {
            citedIndexes.add(Integer.valueOf(matcher.group(1)));
        }
        List<Map<String, Object>> citedSources = new ArrayList<>();
        for (Map<String, Object> source : sources) {
            Object index = source.get("citationIndex");
            if (index instanceof Number && citedIndexes.contains(((Number) index).intValue())) {
                citedSources.add(source);
            }
        }
        return citedSources;
    }

    /**
     * 移除UnknownCitations。
     */
    private String removeUnknownCitations(String content, List<Map<String, Object>> sources) {
        if (StringUtils.isBlank(content)) return content;
        Set<Integer> availableIndexes = new HashSet<>();
        if (sources != null) {
            for (Map<String, Object> source : sources) {
                Object index = source.get("citationIndex");
                if (index instanceof Number) availableIndexes.add(((Number) index).intValue());
            }
        }
        Matcher matcher = CITATION_PATTERN.matcher(content);
        StringBuffer sanitized = new StringBuffer();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            matcher.appendReplacement(sanitized, availableIndexes.contains(index)
                    ? Matcher.quoteReplacement(matcher.group()) : "");
        }
        matcher.appendTail(sanitized);
        return sanitized.toString();
    }

    /**
     * 构建CitationInstruction。
     */
    private String buildCitationInstruction(List<Map<String, Object>> sources, boolean strictGrounding) {
        StringBuilder instruction = new StringBuilder("【可引用来源】仅当回答实际使用了某个片段时，"
                + "请在对应结论后标注其编号，例如【1】。不得编造编号，不使用知识库时不要添加引用。\n");
        if (strictGrounding) {
            instruction.append("当前回答必须完全由这些来源支持；无法从来源得到结论时，请直接说明资料不足。\n");
        }
        for (Map<String, Object> source : sources) {
            instruction.append("【").append(source.get("citationIndex")).append("】")
                    .append(StringUtils.defaultIfBlank((String) source.get("documentName"), "未命名文档"));
            String sectionPath = StringUtils.trimToNull((String) source.get("sectionPath"));
            if (sectionPath != null) instruction.append(" - ").append(sectionPath);
            instruction.append('\n');
        }
        return instruction.toString();
    }

    /**
     * 处理truncate。
     */
    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * 处理stringValue。
     */
    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 处理doubleValue。
     */
    private Double doubleValue(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }

    /**
     * 处理integerValue。
     */
    private Integer integerValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    /**
     * 处理hash查询。
     */
    private String hashQuery(String query) {
        String normalized = StringUtils.defaultString(query).trim().replaceAll("\\s+", " ");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(64);
            for (byte item : digest) {
                value.append(String.format("%02x", item));
            }
            return value.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(normalized.hashCode());
        }
    }
}
