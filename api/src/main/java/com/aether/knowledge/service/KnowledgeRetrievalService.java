package com.aether.knowledge.service;

import com.aether.knowledge.model.KnowledgeRetrievalResult;

public interface KnowledgeRetrievalService {

    KnowledgeRetrievalResult retrieve(String agentDefinitionId, String query);

    default String buildKnowledgeContext(String agentDefinitionId, String query) {
        return retrieve(agentDefinitionId, query).getContext();
    }
}
