package com.aether.evaluation.service.impl;

import com.aether.evaluation.entity.EvaluationResult;
import com.aether.evaluation.entity.EvaluationTask;
import com.aether.evaluation.entity.EvaluationExperiment;
import com.aether.evaluation.service.EvaluationResultCallbackService;
import com.aether.evaluation.service.EvaluationResultService;
import com.aether.evaluation.service.EvaluationTaskService;
import com.aether.evaluation.service.EvaluationWorkerSlotService;
import com.aether.evaluation.service.EvaluationExperimentService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluationResultCallbackServiceImpl implements EvaluationResultCallbackService {
    private final EvaluationResultService resultService; private final EvaluationTaskService taskService; private final EvaluationExperimentAggregationService aggregationService; private final EvaluationWorkerSlotService workerSlotService; private final EvaluationExperimentService experimentService;
    public EvaluationResultCallbackServiceImpl(EvaluationResultService resultService, EvaluationTaskService taskService, EvaluationExperimentAggregationService aggregationService, EvaluationWorkerSlotService workerSlotService, EvaluationExperimentService experimentService) { this.resultService=resultService; this.taskService=taskService; this.aggregationService=aggregationService; this.workerSlotService=workerSlotService; this.experimentService=experimentService; }
    @Override @Transactional(rollbackFor = Exception.class)
    public boolean complete(String runId, String outputJson, String evidenceJson, String metricsJson) {
        EvaluationResult result = resultService.getOne(Wrappers.lambdaQuery(EvaluationResult.class).and(q -> q.eq(EvaluationResult::getRunId, runId).or().eq(EvaluationResult::getWorkflowInstanceId, runId)).last("LIMIT 1"), false);
        if (result == null) return false;
        if ("SUCCEEDED".equals(result.getExecutionStatus()) || "CANCELLED".equals(result.getExecutionStatus()) || "TIMED_OUT".equals(result.getExecutionStatus()) || "BLOCKED".equals(result.getExecutionStatus())) return true;
        EvaluationExperiment experiment=experimentService.getById(result.getExperimentId()); boolean cancelling=experiment!=null&&"CANCELLING".equals(experiment.getStatus()); result.setOutputJson(outputJson); result.setEvidenceJson(evidenceJson); result.setMetricsJson(metricsJson); result.setExecutionStatus("SUCCEEDED"); result.setGradingStatus(cancelling?"SKIPPED":"NOT_STARTED"); resultService.updateById(result); releaseExecutionSlot(result.getId());
        EvaluationTask existing = taskService.getOne(Wrappers.lambdaQuery(EvaluationTask.class).eq(EvaluationTask::getResultId,result.getId()).eq(EvaluationTask::getPhase,"GRADE").eq(EvaluationTask::getDeleted,false),false);
        if(!cancelling&&existing==null){ EvaluationTask task=new EvaluationTask();task.setResultId(result.getId());task.setPhase("GRADE");task.setStatus("READY");task.setGradingRound(result.getActiveGradingRound()==null?0:result.getActiveGradingRound());task.setNextRunAt(System.currentTimeMillis());taskService.save(task); } aggregationService.refresh(result.getExperimentId());
        return true;
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public boolean fail(String runId, String errorCode, String errorMessage) {
        EvaluationResult result=resultService.getOne(Wrappers.lambdaQuery(EvaluationResult.class).and(q -> q.eq(EvaluationResult::getRunId, runId).or().eq(EvaluationResult::getWorkflowInstanceId, runId)).last("LIMIT 1"),false); if(result==null)return false;
        if("FAILED".equals(result.getExecutionStatus())||"TIMED_OUT".equals(result.getExecutionStatus())||"CANCELLED".equals(result.getExecutionStatus()))return true;
        result.setExecutionStatus("FAILED");result.setGradingStatus("SKIPPED");result.setQualityStatus("FAILED");result.setErrorCode(errorCode);result.setErrorMessage(errorMessage);resultService.updateById(result);releaseExecutionSlot(result.getId());aggregationService.refresh(result.getExperimentId());return true;
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public boolean block(String runId, String errorCode) {
        EvaluationResult result=resultService.getOne(Wrappers.lambdaQuery(EvaluationResult.class).and(q -> q.eq(EvaluationResult::getRunId, runId).or().eq(EvaluationResult::getWorkflowInstanceId, runId)).last("LIMIT 1"),false);
        if(result==null)return false;
        String status=result.getExecutionStatus();
        if("SUCCEEDED".equals(status)||"FAILED".equals(status)||"TIMED_OUT".equals(status)||"CANCELLED".equals(status)||"BLOCKED".equals(status))return true;
        result.setExecutionStatus("BLOCKED");result.setGradingStatus("SKIPPED");result.setQualityStatus("FAILED");result.setErrorCode(errorCode);result.setErrorMessage(null);resultService.updateById(result);releaseExecutionSlot(result.getId());aggregationService.refresh(result.getExperimentId());return true;
    }
    private void releaseExecutionSlot(String resultId) { EvaluationTask task=taskService.getOne(Wrappers.lambdaQuery(EvaluationTask.class).eq(EvaluationTask::getResultId,resultId).eq(EvaluationTask::getPhase,"EXECUTE").eq(EvaluationTask::getDeleted,false),false); if(task!=null) workerSlotService.releaseByTask(task.getId()); }
}
