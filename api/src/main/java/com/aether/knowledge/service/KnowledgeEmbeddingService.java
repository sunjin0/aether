package com.aether.knowledge.service;

import com.aether.agent.entity.ModelProvider;

import java.util.List;

/**
 * 定义知识库Embedding业务服务契约。
 */
public interface KnowledgeEmbeddingService {


    /**
     * 处理embed。
     */
    List<Double> embed(ModelProvider provider, String input);

    /**
     * 处理embed全部。
     */
    List<List<Double>> embedAll(ModelProvider provider, List<String> inputs);

    /**
     * 处理toVectorLiteral。
     */
    String toVectorLiteral(List<Double> embedding);
}
