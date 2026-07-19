package com.aether.knowledge.service.impl;

import com.aether.knowledge.entity.KnowledgeAiReview;
import com.aether.knowledge.mapper.KnowledgeAiReviewMapper;
import com.aether.knowledge.service.KnowledgeAiReviewRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeAiReviewRecordServiceImpl extends ServiceImpl<KnowledgeAiReviewMapper, KnowledgeAiReview>
        implements KnowledgeAiReviewRecordService { }
