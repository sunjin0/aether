package com.aether.knowledge.vo;

import com.aether.knowledge.entity.KnowledgeReviewTask;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class KnowledgeReviewTaskVo extends KnowledgeReviewTask {
    private String documentTitle;
    private Integer versionNo;
    private String versionReviewStatus;
}
