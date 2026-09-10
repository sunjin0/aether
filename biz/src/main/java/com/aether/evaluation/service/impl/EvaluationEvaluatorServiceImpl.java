package com.aether.evaluation.service.impl;

import com.aether.evaluation.entity.EvaluationEvaluator;
import com.aether.evaluation.mapper.EvaluationEvaluatorMapper;
import com.aether.evaluation.service.EvaluationEvaluatorService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class EvaluationEvaluatorServiceImpl extends ServiceImpl<EvaluationEvaluatorMapper, EvaluationEvaluator>
        implements EvaluationEvaluatorService { }
