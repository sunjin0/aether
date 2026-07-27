package com.aether.knowledge.service.impl;

import com.aether.knowledge.evaluation.KnowledgeRetrievalMetrics;
import com.aether.knowledge.model.KnowledgeRetrievalEvaluationCase;
import com.aether.knowledge.model.KnowledgeRetrievalEvaluationReport;
import com.aether.knowledge.model.KnowledgeRetrievalResult;
import com.aether.knowledge.service.KnowledgeRetrievalEvaluationService;
import com.aether.knowledge.service.KnowledgeRetrievalService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class KnowledgeRetrievalEvaluationServiceImpl implements KnowledgeRetrievalEvaluationService {
    private final KnowledgeRetrievalService retrievalService;
    public KnowledgeRetrievalEvaluationServiceImpl(KnowledgeRetrievalService retrievalService) { this.retrievalService = retrievalService; }
    @Override
    public KnowledgeRetrievalEvaluationReport evaluate(String agentDefinitionId, List<KnowledgeRetrievalEvaluationCase> cases) {
        KnowledgeRetrievalEvaluationReport report = new KnowledgeRetrievalEvaluationReport();
        List<KnowledgeRetrievalEvaluationCase> valid = cases == null ? Collections.<KnowledgeRetrievalEvaluationCase>emptyList() : cases;
        double recall = 0D, mrr = 0D, ndcg = 0D;
        for (KnowledgeRetrievalEvaluationCase item : valid) {
            if (item == null || StringUtils.isBlank(item.getQuestion())) continue;
            KnowledgeRetrievalResult result = retrievalService.retrieve(agentDefinitionId, item.getQuestion());
            List<String> retrieved = result.getChunks() == null ? Collections.<String>emptyList() : result.getChunks().stream()
                    .map(chunk -> chunk.getId()).filter(StringUtils::isNotBlank).collect(Collectors.toList());
            KnowledgeRetrievalMetrics.Result metrics = KnowledgeRetrievalMetrics.evaluate(
                    new HashSet<String>(item.getExpectedChunkIds() == null ? Collections.<String>emptyList() : item.getExpectedChunkIds()),
                    retrieved, Collections.<String>emptySet(), false);
            KnowledgeRetrievalEvaluationReport.Item outcome = new KnowledgeRetrievalEvaluationReport.Item();
            outcome.setQuestion(item.getQuestion()); outcome.setRetrievedChunkIds(new ArrayList<String>(retrieved));
            outcome.setRecallAtK(metrics.getRecallAtK()); outcome.setMrr(metrics.getMrr()); outcome.setNdcg(metrics.getNdcg());
            report.getItems().add(outcome); recall += metrics.getRecallAtK(); mrr += metrics.getMrr(); ndcg += metrics.getNdcg();
        }
        report.setTotal(report.getItems().size());
        if (report.getTotal() > 0) { report.setRecallAtK(recall / report.getTotal()); report.setMrr(mrr / report.getTotal()); report.setNdcg(ndcg / report.getTotal()); }
        return report;
    }
}
