package com.aether.knowledge.service.impl;

import com.aether.knowledge.entity.KnowledgeReviewActionLog;
import com.aether.knowledge.mapper.KnowledgeReviewActionLogMapper;
import com.aether.knowledge.service.KnowledgeReviewActionLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 实现知识库审核ActionLog业务服务。
 */
@Service
public class KnowledgeReviewActionLogServiceImpl
        extends ServiceImpl<KnowledgeReviewActionLogMapper, KnowledgeReviewActionLog>
        implements KnowledgeReviewActionLogService {
}
