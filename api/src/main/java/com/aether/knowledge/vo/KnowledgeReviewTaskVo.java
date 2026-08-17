package com.aether.knowledge.vo;

import com.aether.knowledge.entity.KnowledgeReviewTask;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表示知识库审核任务VO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KnowledgeReviewTaskVo extends KnowledgeReviewTask {
    private String documentTitle;
    private Integer versionNo;
    private String versionReviewStatus;
}
