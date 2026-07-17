package com.aether.agent.service;

import com.aether.agent.model.ModelChatMessage;
import com.aether.agent.model.ModelStreamResponse;
import com.aether.knowledge.model.KnowledgeRetrievalResult;
import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeRetrievalService;
import com.aether.sys.service.AdminPreferenceService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 负责把用户偏好和知识库检索结果注入模型上下文，并处理回答中的知识库引用。
 */
@Service
public class KnowledgeContextService {
    private static final Pattern CITATION_PATTERN = Pattern.compile("【(\\d+)】");

    private final AdminPreferenceService preferenceService;
    private final KnowledgeRetrievalService retrievalService;
    private final KnowledgeDocumentService documentService;

    public KnowledgeContextService(AdminPreferenceService preferenceService,
                                   KnowledgeRetrievalService retrievalService,
                                   KnowledgeDocumentService documentService) {
        this.preferenceService = preferenceService;
        this.retrievalService = retrievalService;
        this.documentService = documentService;
    }

    /** 将偏好、检索文本和可引用来源添加到模型上下文，并返回来源供 SSE 最终事件使用。 */
    public List<Map<String, Object>> enhance(List<ModelChatMessage> context, String userId, String agentId, String query) {
        List<Map<String, Object>> sources = new ArrayList<>();
        if (context == null) {
            return sources;
        }
        String preferenceContext = preferenceService.buildPreferenceContext(userId);
        if (StringUtils.isNotBlank(preferenceContext)) {
            context.add(new ModelChatMessage("system", preferenceContext));
        }
        KnowledgeRetrievalResult retrieval = retrievalService.retrieve(agentId, query);
        if (retrieval == null) {
            retrieval = new KnowledgeRetrievalResult();
        }
        if (StringUtils.isNotBlank(retrieval.getContext())) {
            context.add(new ModelChatMessage("system", retrieval.getContext()));
        }
        List<KnowledgeDocumentChunk> chunks = retrieval.getChunks() == null
                ? Collections.<KnowledgeDocumentChunk>emptyList() : retrieval.getChunks();
        Map<String, String> documentNames = resolveDocumentNames(chunks);
        for (KnowledgeDocumentChunk chunk : chunks) {
            Map<String, Object> source = new HashMap<>();
            source.put("knowledgeBaseId", chunk.getKnowledgeBaseId());
            source.put("documentId", chunk.getDocumentId());
            source.put("documentName", documentNames.get(chunk.getDocumentId()));
            source.put("citationIndex", sources.size() + 1);
            source.put("chunkId", chunk.getId());
            source.put("chunkIndex", chunk.getChunkIndex());
            source.put("sectionPath", chunk.getSectionPath());
            source.put("content", truncate(chunk.getContent(), 500));
            sources.add(source);
        }
        if (!sources.isEmpty()) {
            context.add(new ModelChatMessage("system", buildCitationInstruction(sources)));
        }
        return sources;
    }

    /** 仅返回回答实际标注的来源；模型未标注时补充来源列表，保证前端可展示依据。 */
    public List<Map<String, Object>> ensureCitations(ModelStreamResponse response, List<Map<String, Object>> sources) {
        List<Map<String, Object>> citedSources = filterCitedSources(response.getContent(), sources);
        if (!citedSources.isEmpty() || sources == null || sources.isEmpty()) {
            return citedSources;
        }
        List<String> labels = new ArrayList<>();
        for (Map<String, Object> source : sources) {
            labels.add("【" + source.get("citationIndex") + "】"
                    + StringUtils.defaultIfBlank((String) source.get("documentName"), "未命名文档"));
        }
        response.setContent(StringUtils.defaultString(response.getContent()) + "\n\n参考来源：" + StringUtils.join(labels, "，"));
        return sources;
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
                names.put(document.getId(), document.getTitle());
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

    private String buildCitationInstruction(List<Map<String, Object>> sources) {
        StringBuilder instruction = new StringBuilder("以下是可引用的知识库来源。仅当回答实际使用了某个片段时，"
                + "请在对应结论后标注其编号，例如【1】。不得编造编号，不使用知识库时不要添加引用。\n");
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
}
