package com.aether.knowledge.service;

import com.aether.knowledge.model.KnowledgeRetrievalEvaluationCase;
import com.aether.knowledge.model.KnowledgeRetrievalEvaluationReport;

import java.util.List;

/**
 * 定义知识库RetrievalEvaluation业务服务契约。
 */
public interface KnowledgeRetrievalEvaluationService {
    /**
     * 处理evaluate。
     */
    KnowledgeRetrievalEvaluationReport evaluate(String agentDefinitionId, List<KnowledgeRetrievalEvaluationCase> cases);
}
