package com.aether.knowledge.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Comparison of two persisted evaluation runs. */
@Data
public class KnowledgeRetrievalEvaluationComparisonVo {
    private String baselineRunId;
    private String candidateRunId;
    private boolean comparable;
    private String nonComparableReason;
    private MetricDelta metrics = new MetricDelta();
    private List<CaseDelta> cases = new ArrayList<CaseDelta>();

    @Data
    public static class MetricDelta {
        private Double baselineRecallAtK;
        private Double candidateRecallAtK;
        private Double recallAtKDelta;
        private Double baselineMrr;
        private Double candidateMrr;
        private Double mrrDelta;
        private Double baselineNdcg;
        private Double candidateNdcg;
        private Double ndcgDelta;
    }

    @Data
    public static class CaseDelta {
        private String evaluationCaseId;
        private String question;
        private String baselineStatus;
        private String candidateStatus;
        private Double baselineRecallAtK;
        private Double candidateRecallAtK;
        private Double recallAtKDelta;
        private Double baselineMrr;
        private Double candidateMrr;
        private Double mrrDelta;
        private Double baselineNdcg;
        private Double candidateNdcg;
        private Double ndcgDelta;
    }
}
