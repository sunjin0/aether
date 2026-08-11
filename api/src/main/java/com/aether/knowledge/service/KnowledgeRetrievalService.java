package com.aether.knowledge.service;

import com.aether.knowledge.model.KnowledgeRetrievalResult;
import com.aether.agent.model.ModelChatMessage;
import java.util.List;
import java.util.Set;

public interface KnowledgeRetrievalService {

    KnowledgeRetrievalResult retrieve(String agentDefinitionId, String query);

    /** Uses recent conversation messages to resolve references before retrieval. */
    default KnowledgeRetrievalResult retrieveWithHistory(String agentDefinitionId, String query,
                                                         List<ModelChatMessage> history) {
        return retrieve(agentDefinitionId, query);
    }

    /** 使用运行期已冻结的知识库范围检索；空集合表示明确禁止知识库检索。 */
    default KnowledgeRetrievalResult retrieve(String agentDefinitionId, String query, Set<String> knowledgeBaseIds) {
        return retrieve(agentDefinitionId, query);
    }

    default String buildKnowledgeContext(String agentDefinitionId, String query) {
        return retrieve(agentDefinitionId, query).getContext();
    }
}
