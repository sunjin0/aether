package com.aether.evaluation.service.impl;
import com.aether.evaluation.entity.EvaluationDatasetVersion; import com.aether.evaluation.mapper.EvaluationDatasetVersionMapper; import com.aether.evaluation.service.EvaluationDatasetVersionService; import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl; import org.springframework.stereotype.Service;
@Service public class EvaluationDatasetVersionServiceImpl extends ServiceImpl<EvaluationDatasetVersionMapper, EvaluationDatasetVersion> implements EvaluationDatasetVersionService { }
