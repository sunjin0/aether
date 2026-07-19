package com.aether.knowledge.service;

import com.aether.agent.entity.ModelProvider;

import java.util.List;

public interface KnowledgeEmbeddingService {


    List<Double> embed(ModelProvider provider, String input);

    List<List<Double>> embedAll(ModelProvider provider, List<String> inputs);

    String toVectorLiteral(List<Double> embedding);
}
