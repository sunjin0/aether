package com.aether.knowledge.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Validation report for the current, mutable evaluation dataset.
 */
@Data
public class KnowledgeRetrievalEvaluationHealthVo {
    private boolean healthy = true;
    private int enabledCaseCount;
    private List<Issue> issues = new ArrayList<>();

    /**
     * 表示Issue。
     */
    @Data
    public static class Issue {
        private String severity;
        private String code;
        private String evaluationCaseId;
        private String message;
    }
}
