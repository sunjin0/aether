package com.aether.knowledge.service.impl;

import com.aether.knowledge.entity.KnowledgeReviewTask;
import com.aether.knowledge.mapper.KnowledgeReviewTaskMapper;
import com.aether.knowledge.service.KnowledgeReviewTaskService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeReviewTaskServiceImpl extends ServiceImpl<KnowledgeReviewTaskMapper, KnowledgeReviewTask>
        implements KnowledgeReviewTaskService {
    @Override
    public boolean claim(String taskId, String reviewerId, long now) {
        return baseMapper.claim(taskId, reviewerId, now) == 1;
    }
}
