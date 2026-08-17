package com.aether.knowledge.service.impl;

import com.aether.knowledge.entity.KnowledgeReviewTask;
import com.aether.knowledge.mapper.KnowledgeReviewTaskMapper;
import com.aether.knowledge.service.KnowledgeReviewTaskService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 实现知识库审核任务业务服务。
 */
@Service
public class KnowledgeReviewTaskServiceImpl extends ServiceImpl<KnowledgeReviewTaskMapper, KnowledgeReviewTask>
        implements KnowledgeReviewTaskService {
    /**
     * 处理claim。
     */
    @Override
    public boolean claim(String taskId, String reviewerId, long now) {
        return baseMapper.claim(taskId, reviewerId, now) == 1;
    }
}
