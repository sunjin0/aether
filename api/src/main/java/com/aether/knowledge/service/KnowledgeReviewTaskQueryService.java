package com.aether.knowledge.service;

import com.aether.knowledge.vo.KnowledgeReviewTaskDetailVo;
import com.aether.knowledge.vo.KnowledgeReviewTaskQueryVo;
import com.aether.knowledge.vo.KnowledgeReviewTaskVo;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 定义知识库审核任务查询业务服务契约。
 */
public interface KnowledgeReviewTaskQueryService {
    /**
     * 查询当前请求。
     */
    IPage<KnowledgeReviewTaskVo> list(KnowledgeReviewTaskQueryVo query);

    /**
     * 详情当前请求。
     */
    KnowledgeReviewTaskDetailVo detail(String taskId);
}
