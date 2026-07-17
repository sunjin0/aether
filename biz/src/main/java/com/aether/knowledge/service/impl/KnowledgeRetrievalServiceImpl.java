package com.aether.knowledge.service.impl;

import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.agent.entity.AgentKnowledgeBaseBinding;
import com.aether.agent.entity.ModelProvider;
import com.aether.knowledge.service.KnowledgeDocumentChunkService;
import com.aether.knowledge.service.KnowledgeEmbeddingService;
import com.aether.agent.service.AgentKnowledgeBaseBindingService;
import com.aether.knowledge.service.KnowledgeBaseService;
import com.aether.knowledge.service.KnowledgeRetrievalService;
import com.aether.knowledge.model.KnowledgeRetrievalResult;
import com.aether.agent.service.ModelProviderService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class KnowledgeRetrievalServiceImpl implements KnowledgeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalServiceImpl.class);
    private static final int STATUS_ENABLED = 1;
    private static final int KB_INDEX_STATUS_DONE = 2;
    private static final int TOP_K = 5;

    private final KnowledgeBaseService knowledgeBaseService;
    private final AgentKnowledgeBaseBindingService bindingService;
    private final KnowledgeDocumentChunkService knowledgeDocumentChunkService;
    private final ModelProviderService modelProviderService;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;

    public KnowledgeRetrievalServiceImpl(KnowledgeBaseService knowledgeBaseService,
                                              AgentKnowledgeBaseBindingService bindingService,
                                              KnowledgeDocumentChunkService knowledgeDocumentChunkService,
                                              ModelProviderService modelProviderService,
                                              KnowledgeEmbeddingService knowledgeEmbeddingService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.bindingService = bindingService;
        this.knowledgeDocumentChunkService = knowledgeDocumentChunkService;
        this.modelProviderService = modelProviderService;
        this.knowledgeEmbeddingService = knowledgeEmbeddingService;
    }

    @Override
    public KnowledgeRetrievalResult retrieve(String agentDefinitionId, String query) {
        KnowledgeRetrievalResult result = new KnowledgeRetrievalResult();
        if (StringUtils.isBlank(agentDefinitionId) || StringUtils.isBlank(query)) {
            return result;
        }
        try {
            List<AgentKnowledgeBaseBinding> bindings = bindingService.list(Wrappers.lambdaQuery(AgentKnowledgeBaseBinding.class)
                    .eq(AgentKnowledgeBaseBinding::getAgentDefinitionId, agentDefinitionId)
                    .eq(AgentKnowledgeBaseBinding::getStatus, STATUS_ENABLED)
                    .eq(AgentKnowledgeBaseBinding::getDeleted, false));
            List<String> boundKbIds = bindings == null ? new ArrayList<>() : bindings.stream()
                    .map(AgentKnowledgeBaseBinding::getKnowledgeBaseId)
                    .filter(StringUtils::isNotBlank)
                    .distinct()
                    .collect(Collectors.toList());
            List<KnowledgeBase> platformBases = knowledgeBaseService.list(Wrappers.lambdaQuery(KnowledgeBase.class)
                    .eq(KnowledgeBase::getScope, "PLATFORM").eq(KnowledgeBase::getStatus, STATUS_ENABLED)
                    .eq(KnowledgeBase::getIndexStatus, KB_INDEX_STATUS_DONE).eq(KnowledgeBase::getDeleted, false));
            if (platformBases != null) platformBases.forEach(item -> boundKbIds.add(item.getId()));
            if (boundKbIds.isEmpty()) return result;
            List<KnowledgeBase> knowledgeBases = knowledgeBaseService.list(Wrappers.lambdaQuery(KnowledgeBase.class)
                    .in(KnowledgeBase::getId, boundKbIds)
                    .eq(KnowledgeBase::getStatus, STATUS_ENABLED)
                    .eq(KnowledgeBase::getIndexStatus, KB_INDEX_STATUS_DONE)
                    .eq(KnowledgeBase::getDeleted, false));
            if (knowledgeBases == null || knowledgeBases.isEmpty()) {
                return result;
            }
            Map<String, ModelProvider> providers = new LinkedHashMap<>();
            Map<String, List<String>> knowledgeBaseIdsByProvider = new LinkedHashMap<>();
            for (KnowledgeBase knowledgeBase : knowledgeBases) {
                ModelProvider provider = getEmbeddingProvider(knowledgeBase);
                if (provider == null || Boolean.TRUE.equals(provider.getDeleted())
                        || !Integer.valueOf(STATUS_ENABLED).equals(provider.getStatus())) {
                    log.warn("知识库未配置可用的向量模型供应商: knowledgeBaseId={}", knowledgeBase.getId());
                    continue;
                }
                providers.put(provider.getId(), provider);
                knowledgeBaseIdsByProvider.computeIfAbsent(provider.getId(), key -> new ArrayList<>())
                        .add(knowledgeBase.getId());
            }

            List<KnowledgeDocumentChunk> chunks = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : knowledgeBaseIdsByProvider.entrySet()) {
                ModelProvider provider = providers.get(entry.getKey());
                try {
                    String vector = knowledgeEmbeddingService.toVectorLiteral(knowledgeEmbeddingService.embed(provider, query));
                    chunks.addAll(knowledgeDocumentChunkService.searchSimilarChunks(entry.getValue(), vector, TOP_K));
                } catch (Exception e) {
                    // One unavailable embedding provider must not prevent other
                    // knowledge bases from participating in retrieval.
                    log.warn("知识库向量检索失败，已跳过该供应商: providerId={}", provider.getId(), e);
                }
            }
            if (chunks.isEmpty()) {
                return result;
            }
            StringBuilder builder = new StringBuilder("【知识库检索结果】\n");
            int i = 1;
            for (KnowledgeDocumentChunk chunk : chunks) {
                builder.append("片段 ").append(i++).append("：\n")
                        .append(chunk.getContent()).append("\n\n");
            }
            builder.append("请优先依据以上知识片段回答；若片段不足以支持结论，请明确说明。");
            result.setContext(builder.toString());
            result.setChunks(chunks);
            return result;
        } catch (Exception e) {
            log.warn("知识库检索失败，已降级为无知识上下文: agentId={}", agentDefinitionId, e);
            return result;
        }
    }

    private ModelProvider getEmbeddingProvider(KnowledgeBase knowledgeBase) {
        if (StringUtils.isNotBlank(knowledgeBase.getEmbeddingProviderId())) {
            return modelProviderService.getById(knowledgeBase.getEmbeddingProviderId());
        }
        List<ModelProvider> providers = modelProviderService.list(Wrappers.lambdaQuery(ModelProvider.class)
                .eq(ModelProvider::getStatus, STATUS_ENABLED)
                .eq(ModelProvider::getDeleted, false)
                .orderByAsc(ModelProvider::getSortNum));
        return providers == null || providers.isEmpty() ? null : providers.get(0);
    }
}
