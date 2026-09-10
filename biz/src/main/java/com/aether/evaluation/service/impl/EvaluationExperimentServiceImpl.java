package com.aether.evaluation.service.impl;
import com.aether.evaluation.entity.EvaluationExperiment; import com.aether.evaluation.mapper.EvaluationExperimentMapper; import com.aether.evaluation.service.EvaluationExperimentService; import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl; import org.springframework.stereotype.Service;
@Service public class EvaluationExperimentServiceImpl extends ServiceImpl<EvaluationExperimentMapper, EvaluationExperiment> implements EvaluationExperimentService { }
