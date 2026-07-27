package com.aether.knowledge.service;

import com.aether.knowledge.model.KnowledgeRetrievalEvaluationCase;
import com.aether.knowledge.model.KnowledgeRetrievalEvaluationReport;
import java.util.List;

public interface KnowledgeRetrievalEvaluationService {
    KnowledgeRetrievalEvaluationReport evaluate(String agentDefinitionId, List<KnowledgeRetrievalEvaluationCase> cases);
}
