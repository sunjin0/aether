package com.aether.knowledge.service.impl;

import com.aether.knowledge.entity.KnowledgeAiReviewIssue;
import com.aether.knowledge.mapper.KnowledgeAiReviewIssueMapper;
import com.aether.knowledge.service.KnowledgeAiReviewIssueService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 实现知识库Ai审核Issue业务服务。
 */
@Service
public class KnowledgeAiReviewIssueServiceImpl extends ServiceImpl<KnowledgeAiReviewIssueMapper, KnowledgeAiReviewIssue>
        implements KnowledgeAiReviewIssueService {
}
