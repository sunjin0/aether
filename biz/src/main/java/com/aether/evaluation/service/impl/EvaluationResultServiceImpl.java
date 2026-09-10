package com.aether.evaluation.service.impl;
import com.aether.evaluation.entity.EvaluationResult; import com.aether.evaluation.mapper.EvaluationResultMapper; import com.aether.evaluation.service.EvaluationResultService; import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl; import org.springframework.stereotype.Service;
@Service public class EvaluationResultServiceImpl extends ServiceImpl<EvaluationResultMapper, EvaluationResult> implements EvaluationResultService { }
