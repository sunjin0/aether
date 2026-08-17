package com.aether.knowledge.service.impl;

import com.aether.knowledge.entity.KnowledgeIndexJob;
import com.aether.knowledge.mapper.KnowledgeIndexJobMapper;
import com.aether.knowledge.service.KnowledgeIndexJobService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 实现知识库索引Job业务服务。
 */
@Service
public class KnowledgeIndexJobServiceImpl extends ServiceImpl<KnowledgeIndexJobMapper, KnowledgeIndexJob>
        implements KnowledgeIndexJobService {

    /**
     * 处理claimPending。
     */
    @Override
    public boolean claimPending(String jobId, long startedAt) {
        return baseMapper.claimPending(jobId, startedAt) == 1;
    }
}
