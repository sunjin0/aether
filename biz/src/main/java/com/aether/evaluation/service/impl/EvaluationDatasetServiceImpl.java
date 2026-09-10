package com.aether.evaluation.service.impl;

import com.aether.evaluation.entity.EvaluationDataset;
import com.aether.evaluation.mapper.EvaluationDatasetMapper;
import com.aether.evaluation.service.EvaluationDatasetService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class EvaluationDatasetServiceImpl extends ServiceImpl<EvaluationDatasetMapper, EvaluationDataset>
        implements EvaluationDatasetService { }
