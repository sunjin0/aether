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
/** 执行离线检索评测，不调用回答生成模型。 */
public class KnowledgeRetrievalEvaluationServiceImpl implements KnowledgeRetrievalEvaluationService {
    private final KnowledgeRetrievalService retrievalService;
    /** 评测复用线上检索服务，保证离线指标与真实业务链路一致。 */
    public KnowledgeRetrievalEvaluationServiceImpl(KnowledgeRetrievalService retrievalService) { this.retrievalService = retrievalService; }
    @Override
    /** 批量检索评测问题并计算总体平均指标。 */
    public KnowledgeRetrievalEvaluationReport evaluate(String agentDefinitionId, List<KnowledgeRetrievalEvaluationCase> cases) {
        KnowledgeRetrievalEvaluationReport report = new KnowledgeRetrievalEvaluationReport();
        List<KnowledgeRetrievalEvaluationCase> valid = cases == null ? Collections.<KnowledgeRetrievalEvaluationCase>emptyList() : cases;
        double recall = 0D, mrr = 0D, ndcg = 0D;
        for (KnowledgeRetrievalEvaluationCase item : valid) {
            if (item == null || StringUtils.isBlank(item.getQuestion())) continue;
            // 直接调用 Agent 当前检索配置，避免评测逻辑和线上检索逻辑出现偏差。
            KnowledgeRetrievalResult result = retrievalService.retrieve(agentDefinitionId, item.getQuestion());
            List<String> retrieved = result.getChunks() == null ? Collections.<String>emptyList() : result.getChunks().stream()
                    .map(chunk -> chunk.getId()).filter(StringUtils::isNotBlank).collect(Collectors.toList());
            // 评测阶段只关注检索命中，不进行答案引用和 grounded 判定。
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
