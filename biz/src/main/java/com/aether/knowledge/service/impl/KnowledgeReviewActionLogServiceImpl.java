package com.aether.knowledge.service.impl;

import com.aether.knowledge.entity.KnowledgeReviewActionLog;
import com.aether.knowledge.mapper.KnowledgeReviewActionLogMapper;
import com.aether.knowledge.service.KnowledgeReviewActionLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeReviewActionLogServiceImpl
        extends ServiceImpl<KnowledgeReviewActionLogMapper, KnowledgeReviewActionLog>
        implements KnowledgeReviewActionLogService { }
