package com.aether.evaluation.service.impl;

import com.aether.evaluation.entity.EvaluationReview;
import com.aether.evaluation.mapper.EvaluationReviewMapper;
import com.aether.evaluation.service.EvaluationReviewService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class EvaluationReviewServiceImpl extends ServiceImpl<EvaluationReviewMapper, EvaluationReview>
        implements EvaluationReviewService { }
