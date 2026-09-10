package com.aether.evaluation.service.impl;

import com.aether.evaluation.entity.EvaluationExperiment;
import com.aether.evaluation.entity.EvaluationResult;
import com.aether.evaluation.service.EvaluationExperimentService;
import com.aether.evaluation.service.EvaluationResultService;
import com.aether.evaluation.service.EvaluationPolicyService;
import com.aether.evaluation.service.EvaluationCaseVersionService;
import com.aether.evaluation.entity.EvaluationCaseVersion;
import com.aether.evaluation.entity.EvaluationPolicy;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

@Service
public class EvaluationExperimentAggregationService {
    private final EvaluationExperimentService experimentService; private final EvaluationResultService resultService; private final EvaluationPolicyService policyService; private final EvaluationCaseVersionService caseVersionService;
    public EvaluationExperimentAggregationService(EvaluationExperimentService experimentService, EvaluationResultService resultService, EvaluationPolicyService policyService, EvaluationCaseVersionService caseVersionService){this.experimentService=experimentService;this.resultService=resultService;this.policyService=policyService;this.caseVersionService=caseVersionService;}
    public void refresh(String experimentId){
        EvaluationExperiment experiment=experimentService.getById(experimentId); if(experiment==null)return;
        List<EvaluationResult> rows=resultService.list(Wrappers.lambdaQuery(EvaluationResult.class).eq(EvaluationResult::getExperimentId,experimentId).eq(EvaluationResult::getDeleted,false));
        long succeeded=rows.stream().filter(r->"SUCCEEDED".equals(r.getExecutionStatus())).count(), failed=rows.stream().filter(r->"FAILED".equals(r.getExecutionStatus())||"TIMED_OUT".equals(r.getExecutionStatus())||"BLOCKED".equals(r.getExecutionStatus())||"CANCELLED".equals(r.getExecutionStatus())||"UNKNOWN".equals(r.getExecutionStatus())).count();
        List<EvaluationResult> scored=rows.stream().filter(r->r.getScore()!=null).toList();
        Set<String> caseVersionIds=rows.stream().map(EvaluationResult::getCaseVersionId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Map<String, EvaluationCaseVersion> cases=caseVersionIds.isEmpty()?Collections.emptyMap():caseVersionService.listByIds(caseVersionIds).stream().collect(java.util.stream.Collectors.toMap(EvaluationCaseVersion::getId, item->item));
        Map<String,List<EvaluationResult>> byCase=rows.stream().collect(java.util.stream.Collectors.groupingBy(EvaluationResult::getCaseVersionId));
        long completeCases=byCase.values().stream().filter(units->units.stream().allMatch(unit->"SUCCEEDED".equals(unit.getExecutionStatus())&&"SUCCEEDED".equals(unit.getGradingStatus())&&unit.getScore()!=null)).count();
        long passedCases=byCase.entrySet().stream().filter(entry->entry.getValue().stream().allMatch(this::isPassedUnit)).count();
        BigDecimal avg=byCase.values().stream().filter(units->units.stream().allMatch(unit->"SUCCEEDED".equals(unit.getExecutionStatus())&&"SUCCEEDED".equals(unit.getGradingStatus())&&unit.getScore()!=null)).map(units->units.stream().map(EvaluationResult::getScore).reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(units.size()),2,java.math.RoundingMode.HALF_UP)).reduce(BigDecimal::add).map(sum->sum.divide(BigDecimal.valueOf(completeCases),2,java.math.RoundingMode.HALF_UP)).orElse(null);
        BigDecimal passRate=byCase.isEmpty()?null:BigDecimal.valueOf(passedCases*100).divide(BigDecimal.valueOf(byCase.size()),2,java.math.RoundingMode.HALF_UP);
        Map<String,Object> counts=new LinkedHashMap<>();counts.put("total",rows.size());counts.put("succeeded",succeeded);counts.put("failed",failed);counts.put("scored",scored.size());counts.put("caseTotal",byCase.size());counts.put("casePassed",passedCases);counts.put("caseCompleteScored",completeCases);
        List<Long> executionDurations=rows.stream().map(this::executionDuration).filter(Objects::nonNull).sorted().collect(java.util.stream.Collectors.toList());
        Map<String,Object> metrics=new LinkedHashMap<>();metrics.put("executionSuccessRate",rows.isEmpty()?BigDecimal.ZERO:BigDecimal.valueOf(succeeded).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(rows.size()),2,java.math.RoundingMode.HALF_UP));metrics.put("averageScore",avg);metrics.put("scoreCoverage",rows.isEmpty()?BigDecimal.ZERO:BigDecimal.valueOf(scored.size()).divide(BigDecimal.valueOf(rows.size()),4,java.math.RoundingMode.HALF_UP));metrics.put("casePassRate",passRate);metrics.put("caseScoreCoverage",byCase.isEmpty()?BigDecimal.ZERO:BigDecimal.valueOf(completeCases).divide(BigDecimal.valueOf(byCase.size()),4,java.math.RoundingMode.HALF_UP));metrics.put("executionDurationCoverage",rows.isEmpty()?BigDecimal.ZERO:BigDecimal.valueOf(executionDurations.size()).divide(BigDecimal.valueOf(rows.size()),4,java.math.RoundingMode.HALF_UP));metrics.put("executionDurationP50Ms",nearestRank(executionDurations,50));metrics.put("executionDurationP95Ms",nearestRank(executionDurations,95));
        String newCounts=com.alibaba.fastjson2.JSON.toJSONString(counts),newMetrics=com.alibaba.fastjson2.JSON.toJSONString(metrics);boolean reportChanged=!Objects.equals(experiment.getCountsJson(),newCounts)||!Objects.equals(experiment.getMetricsJson(),newMetrics);experiment.setCountsJson(newCounts);experiment.setMetricsJson(newMetrics);
        boolean finished=!rows.isEmpty()&&rows.stream().allMatch(r->{String s=r.getExecutionStatus();if(!("SUCCEEDED".equals(s)||"FAILED".equals(s)||"TIMED_OUT".equals(s)||"BLOCKED".equals(s)||"CANCELLED".equals(s)||"UNKNOWN".equals(s)))return false;return !"SUCCEEDED".equals(s)||"SUCCEEDED".equals(r.getGradingStatus())||"FAILED".equals(r.getGradingStatus())||"SKIPPED".equals(r.getGradingStatus());});
        if(finished){
            EvaluationPolicy policy=policyService.getOne(Wrappers.lambdaQuery(EvaluationPolicy.class).eq(EvaluationPolicy::getTargetType,experiment.getTargetType()).eq(EvaluationPolicy::getTargetId,experiment.getTargetId()).eq(EvaluationPolicy::getDeleted,false),false);
            boolean reviewRequired=policy!=null&&Boolean.TRUE.equals(policy.getRequireReview());
            boolean reviewComplete=!reviewRequired||rows.stream().allMatch(r->"APPROVED".equals(r.getReviewStatus())||"CANCELLED".equals(r.getExecutionStatus()));
            boolean requiredPassed=byCase.entrySet().stream().filter(entry->{EvaluationCaseVersion item=cases.get(entry.getKey());return item!=null&&Boolean.TRUE.equals(item.getRequired());}).allMatch(entry->entry.getValue().stream().allMatch(this::isPassedUnit));
            com.alibaba.fastjson2.JSONObject config;try{config=com.alibaba.fastjson2.JSONObject.parseObject(experiment.getConfigJson());}catch(RuntimeException ex){config=new com.alibaba.fastjson2.JSONObject();}BigDecimal minimumScore=config.getBigDecimal("minimumScore"),minimumPassRate=config.getBigDecimal("minimumPassRate");if(minimumScore==null)minimumScore=BigDecimal.valueOf(80);if(minimumPassRate==null)minimumPassRate=BigDecimal.valueOf(100);
            boolean incomplete=completeCases!=byCase.size()||rows.stream().anyMatch(row->"CANCELLED".equals(row.getExecutionStatus())||"UNKNOWN".equals(row.getExecutionStatus()));
            if("CANCELLING".equals(experiment.getStatus())){experiment.setStatus("CANCELLED");experiment.setQualityStatus("FAILED");}else{experiment.setStatus("COMPLETED");experiment.setQualityStatus(incomplete?"INCOMPLETE":requiredPassed&&reviewComplete&&avg!=null&&avg.compareTo(minimumScore)>=0&&passRate!=null&&passRate.compareTo(minimumPassRate)>=0?"PASSED":"FAILED");}if(experiment.getFinishedAt()==null)experiment.setFinishedAt(System.currentTimeMillis());}
        experiment.setRevision((experiment.getRevision()==null?0:experiment.getRevision())+1);if(reportChanged)experiment.setReportRevision((experiment.getReportRevision()==null?0:experiment.getReportRevision())+1);experimentService.updateById(experiment);
    }
    private Long executionDuration(EvaluationResult result){try{com.alibaba.fastjson2.JSONObject metrics=com.alibaba.fastjson2.JSONObject.parseObject(result.getMetricsJson());if(metrics==null)return null;Long latency=metrics.getLong("latencyMs");return latency==null?metrics.getLong("latency_ms"):latency;}catch(RuntimeException ignored){return null;}}
    /** A hard assertion failure is a unit failure even when its output score remains high. */
    private boolean isPassedUnit(EvaluationResult unit){return "SUCCEEDED".equals(unit.getExecutionStatus())&&"SUCCEEDED".equals(unit.getGradingStatus())&&"PASSED".equals(unit.getQualityStatus())&&unit.getScore()!=null;}
    private Long nearestRank(List<Long> values,int percentile){if(values.isEmpty())return null;int index=(int)Math.ceil(values.size()*percentile/100.0d)-1;return values.get(Math.max(0,index));}
}
