package com.aether.knowledge.vo;

import com.aether.knowledge.entity.KnowledgeRetrievalEvaluationCaseEntity;
import com.aether.knowledge.entity.KnowledgeRetrievalEvaluationLabel;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/** Portable JSON representation of a draft evaluation case and its positive labels. */
@Data
public class KnowledgeRetrievalEvaluationCaseTransferVo {
    private KnowledgeRetrievalEvaluationCaseEntity item;
    private List<KnowledgeRetrievalEvaluationLabel> labels = new ArrayList<>();
}
