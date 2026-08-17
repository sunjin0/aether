package com.aether.knowledge.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Row-level import validation response.
 */
@Data
public class KnowledgeRetrievalEvaluationImportPreviewVo {
    private boolean valid = true;
    private int acceptedCount;
    private List<RowIssue> issues = new ArrayList<>();

    /**
     * 表示RowIssue。
     */
    @Data
    public static class RowIssue {
        private int row;
        private String code;
        private String message;
    }
}
