package com.aether.knowledge.service.impl;

import com.aether.knowledge.entity.KnowledgeAiReview;
import com.aether.knowledge.mapper.KnowledgeAiReviewMapper;
import com.aether.knowledge.service.KnowledgeAiReviewRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 实现知识库Ai审核Record业务服务。
 */
@Service
public class KnowledgeAiReviewRecordServiceImpl extends ServiceImpl<KnowledgeAiReviewMapper, KnowledgeAiReview>
        implements KnowledgeAiReviewRecordService {
}
