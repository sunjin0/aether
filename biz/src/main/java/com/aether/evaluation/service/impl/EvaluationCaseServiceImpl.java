package com.aether.evaluation.service.impl;
import com.aether.evaluation.entity.EvaluationCase; import com.aether.evaluation.mapper.EvaluationCaseMapper; import com.aether.evaluation.service.EvaluationCaseService; import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl; import org.springframework.stereotype.Service;
@Service public class EvaluationCaseServiceImpl extends ServiceImpl<EvaluationCaseMapper, EvaluationCase> implements EvaluationCaseService { }
