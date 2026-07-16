package com.aether.knowledge.service;

public interface KnowledgeRetrievalService {

    String buildKnowledgeContext(String agentDefinitionId, String query);
}
