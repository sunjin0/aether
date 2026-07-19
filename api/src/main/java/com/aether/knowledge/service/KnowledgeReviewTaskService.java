package com.aether.knowledge.service;

import com.aether.knowledge.entity.KnowledgeReviewTask;
import com.baomidou.mybatisplus.extension.service.IService;

public interface KnowledgeReviewTaskService extends IService<KnowledgeReviewTask> {
    boolean claim(String taskId, String reviewerId, long now);
}
