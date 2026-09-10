package com.aether.evaluation.service.impl;
import com.aether.evaluation.entity.EvaluationScore; import com.aether.evaluation.mapper.EvaluationScoreMapper; import com.aether.evaluation.service.EvaluationScoreService; import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl; import org.springframework.stereotype.Service;
@Service public class EvaluationScoreServiceImpl extends ServiceImpl<EvaluationScoreMapper, EvaluationScore> implements EvaluationScoreService { }
