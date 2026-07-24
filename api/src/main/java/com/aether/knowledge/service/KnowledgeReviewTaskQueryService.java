package com.aether.knowledge.service;

import com.aether.knowledge.vo.KnowledgeReviewTaskDetailVo;
import com.aether.knowledge.vo.KnowledgeReviewTaskQueryVo;
import com.aether.knowledge.vo.KnowledgeReviewTaskVo;
import com.baomidou.mybatisplus.core.metadata.IPage;

public interface KnowledgeReviewTaskQueryService {
    IPage<KnowledgeReviewTaskVo> list(KnowledgeReviewTaskQueryVo query);

    KnowledgeReviewTaskDetailVo detail(String taskId);
}
