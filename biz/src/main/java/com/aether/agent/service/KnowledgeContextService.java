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

/**
 * 负责把用户偏好和知识库检索结果注入模型上下文，并处理回答中的知识库引用。
 */
@Service
public class KnowledgeContextService {
    private static final Pattern CITATION_PATTERN = Pattern.compile("【(\\d+)】");

    private final AdminPreferenceService preferenceService;
    private final KnowledgeRetrievalService retrievalService;
    private final KnowledgeDocumentService documentService;
    private final KnowledgeReferenceLogMapper referenceLogMapper;
    private final KnowledgeRetrievalLogMapper retrievalLogMapper;

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

    /** 兼容不需要持久化引用记录的轻量调用方和单元测试。 */
    public KnowledgeContextService(AdminPreferenceService preferenceService,
                                   KnowledgeRetrievalService retrievalService,
                                   KnowledgeDocumentService documentService) {
        this(preferenceService, retrievalService, documentService, null, null);
    }

    public KnowledgeContextService(AdminPreferenceService preferenceService,
                                   KnowledgeRetrievalService retrievalService,
                                   KnowledgeDocumentService documentService,
                                   KnowledgeReferenceLogMapper referenceLogMapper) {
        this(preferenceService, retrievalService, documentService, referenceLogMapper, null);
    }

    /** 将用户偏好、检索文本和可引用来源注入模型上下文，并返回来源供 SSE 最终事件使用。 */
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
        String preferenceContext = preferenceService.buildPreferenceContext(userId, null, conversationId);
        if (StringUtils.isNotBlank(preferenceContext)) {
            context.add(insertIndex++, new ModelChatMessage("system", preferenceContext));
        }
        KnowledgeRetrievalResult retrieval = retrievalService.retrieve(agentId, query);
        if (retrieval == null) {
            retrieval = new KnowledgeRetrievalResult();
        }
        if (StringUtils.isNotBlank(retrieval.getContext())) {
            context.add(insertIndex++, new ModelChatMessage("system", retrieval.getContext()));
        } else if (retrieval.isRetrievalAttempted()) {
            context.add(insertIndex++, new ModelChatMessage("system",
                    retrieval.isStrictGrounding()
                            ? "本轮未检索到足以支撑回答的知识库片段。当前 Agent 只能基于知识库资料回答；"
                            + "请明确说明资料不足，并请求用户补充资料或换一种表述，不得使用模型固有知识作答。"
                            : "本轮未检索到足以支撑回答的知识库片段。不得将推测或模型固有知识表述为知识库结论；"
                            + "如果用户要求依据知识库回答，请明确说明当前资料不足，并在必要时请求补充信息。"));
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
        if (!sources.isEmpty()) {
            context.add(insertIndex++, new ModelChatMessage("system", buildCitationInstruction(sources, retrieval.isStrictGrounding())));
        }
        return sources;
    }

    /** 仅返回回答实际标注的来源；不修改回答内容。 */
    public List<Map<String, Object>> ensureCitations(ModelStreamResponse response, List<Map<String, Object>> sources) {
        return filterCitedSources(response.getContent(), sources);
    }

    /** 非流式版本：计算引用来源，不修改回答内容。 */
    public List<Map<String, Object>> ensureCitations(ModelChatResponse response, List<Map<String, Object>> sources) {
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

    /** Records candidate-level retrieval outcomes without persisting raw user queries. */
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
                log.setAgentDefinitionId(agentId); log.setConversationId(conversationId); log.setMessageId(messageId);
                log.setQueryHash(queryHash); log.setCited(false); log.setOutcome("NO_MATCH"); log.setRetrievedAt(now);
                retrievalLogMapper.insert(log);
                return;
            }
            for (Map<String, Object> source : retrievedSources) {
                KnowledgeRetrievalLog log = new KnowledgeRetrievalLog();
                String chunkId = stringValue(source.get("chunkId"));
                log.setAgentDefinitionId(agentId); log.setConversationId(conversationId); log.setMessageId(messageId);
                log.setQueryHash(queryHash); log.setKnowledgeBaseId(stringValue(source.get("knowledgeBaseId")));
                log.setDocumentId(stringValue(source.get("documentId"))); log.setChunkId(chunkId);
                log.setSimilarity(doubleValue(source.get("similarity")));
                log.setRetrievalScore(doubleValue(source.get("retrievalScore")));
                log.setCited(StringUtils.isNotBlank(chunkId) && citedChunkIds.contains(chunkId));
                log.setOutcome("MATCHED"); log.setRetrievedAt(now);
                retrievalLogMapper.insert(log);
            }
        } catch (Exception ignored) {
            // Observability failures must not affect a successful response.
        }
    }

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

    private String buildCitationInstruction(List<Map<String, Object>> sources, boolean strictGrounding) {
        StringBuilder instruction = new StringBuilder("以下是可引用的知识库来源。仅当回答实际使用了某个片段时，"
                + "请在对应结论后标注其编号，例如【1】。不得编造编号，不使用知识库时不要添加引用。\n");
        if (strictGrounding) {
            instruction.append("当前回答必须完全由这些来源支持；无法从来源得到结论时，请直接说明资料不足。\n");
        }
        for (Map<String, Object> source : sources) {
            instruction.append("【").append(source.get("citationIndex")).append("】")
                    .append(StringUtils.defaultIfBlank((String) source.get("documentName"), "未命名文档"))
                    .append(" - ").append(StringUtils.defaultString((String) source.get("sectionPath"))).append('\n');
        }
        return instruction.toString();
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Double doubleValue(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }

    private Integer integerValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

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
