package com.aether.knowledge.vo;

import com.aether.knowledge.entity.KnowledgeRetrievalEvaluationRun;
import com.aether.knowledge.entity.KnowledgeRetrievalEvaluationSet;
import com.aether.knowledge.entity.KnowledgeRetrievalEvaluationSetVersion;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * One consistent snapshot for the retrieval evaluation workbench.
 */
@Data
public class KnowledgeRetrievalEvaluationWorkbenchVo {
    private KnowledgeRetrievalEvaluationSet evaluationSet;
    private KnowledgeRetrievalEvaluationHealthVo health;
    private List<KnowledgeRetrievalEvaluationSetVersion> versions = new ArrayList<>();
    private List<KnowledgeRetrievalEvaluationRun> runs = new ArrayList<>();
    private List<KnowledgeRetrievalEvaluationRun> trend = new ArrayList<>();
}
