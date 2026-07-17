package com.aether.knowledge.service.impl;

import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.agent.entity.AgentKnowledgeBaseBinding;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.knowledge.service.KnowledgeDocumentChunkService;
import com.aether.knowledge.service.KnowledgeEmbeddingService;
import com.aether.agent.service.AgentKnowledgeBaseBindingService;
import com.aether.knowledge.service.KnowledgeBaseService;
import com.aether.knowledge.service.KnowledgeRetrievalService;
import com.aether.agent.service.ModelProviderService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
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
    private final AgentDefinitionService agentDefinitionService;
    private final ModelProviderService modelProviderService;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;

    public KnowledgeRetrievalServiceImpl(KnowledgeBaseService knowledgeBaseService,
                                              AgentKnowledgeBaseBindingService bindingService,
                                              KnowledgeDocumentChunkService knowledgeDocumentChunkService,
                                              AgentDefinitionService agentDefinitionService,
                                              ModelProviderService modelProviderService,
                                              KnowledgeEmbeddingService knowledgeEmbeddingService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.bindingService = bindingService;
        this.knowledgeDocumentChunkService = knowledgeDocumentChunkService;
        this.agentDefinitionService = agentDefinitionService;
        this.modelProviderService = modelProviderService;
        this.knowledgeEmbeddingService = knowledgeEmbeddingService;
    }

    @Override
    public String buildKnowledgeContext(String agentDefinitionId, String query) {
        if (StringUtils.isBlank(agentDefinitionId) || StringUtils.isBlank(query)) {
            return null;
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
            if (boundKbIds.isEmpty()) return null;
            List<KnowledgeBase> knowledgeBases = knowledgeBaseService.list(Wrappers.lambdaQuery(KnowledgeBase.class)
                    .in(KnowledgeBase::getId, boundKbIds)
                    .eq(KnowledgeBase::getStatus, STATUS_ENABLED)
                    .eq(KnowledgeBase::getIndexStatus, KB_INDEX_STATUS_DONE)
                    .eq(KnowledgeBase::getDeleted, false));
            if (knowledgeBases == null || knowledgeBases.isEmpty()) {
                return null;
            }
            AgentDefinition agent = agentDefinitionService.getById(agentDefinitionId);
            if (agent == null) {
                return null;
            }
            ModelProvider provider = modelProviderService.getById(agent.getModelProviderId());
            String vector = knowledgeEmbeddingService.toVectorLiteral(knowledgeEmbeddingService.embed(provider, query));
            List<String> kbIds = knowledgeBases.stream().map(KnowledgeBase::getId).collect(Collectors.toList());
            List<KnowledgeDocumentChunk> chunks = knowledgeDocumentChunkService.searchSimilarChunks(kbIds, vector, TOP_K);
            if (chunks == null || chunks.isEmpty()) {
                return null;
            }
            StringBuilder builder = new StringBuilder("【知识库检索结果】\n");
            int i = 1;
            for (KnowledgeDocumentChunk chunk : chunks) {
                builder.append("片段 ").append(i++).append("：\n")
                        .append(chunk.getContent()).append("\n\n");
            }
            builder.append("请优先依据以上知识片段回答；若片段不足以支持结论，请明确说明。");
            return builder.toString();
        } catch (Exception e) {
            log.warn("知识库检索失败，已降级为无知识上下文: agentId={}", agentDefinitionId, e);
            return null;
        }
    }
}
