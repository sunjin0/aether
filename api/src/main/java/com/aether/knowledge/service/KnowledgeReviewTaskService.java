package com.aether.knowledge.service;

import com.aether.knowledge.entity.KnowledgeReviewTask;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 定义知识库审核任务业务服务契约。
 */
public interface KnowledgeReviewTaskService extends IService<KnowledgeReviewTask> {
    /**
     * 处理claim。
     */
    boolean claim(String taskId, String reviewerId, long now);
}
