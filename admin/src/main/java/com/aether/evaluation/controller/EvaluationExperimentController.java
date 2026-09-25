package com.aether.evaluation.controller;

import com.aether.entity.WebResponse;
import com.aether.evaluation.entity.EvaluationExperiment;
import com.aether.evaluation.service.EvaluationExperimentService;
import com.aether.evaluation.service.EvaluationResultService;
import com.aether.evaluation.entity.EvaluationResult;
import com.aether.evaluation.entity.EvaluationCaseVersion;
import com.aether.evaluation.entity.EvaluationTask;
import com.aether.evaluation.service.EvaluationCaseVersionService;
import com.aether.evaluation.service.EvaluationTaskService;
import com.aether.evaluation.service.EvaluationWorkerSlotService;
import com.aether.evaluation.service.EvaluationSnapshotService;
import com.aether.evaluation.service.EvaluationScoreService;
import com.aether.evaluation.service.EvaluationTargetAdapter;
import com.aether.evaluation.service.EvaluationDatasetService;
import com.aether.evaluation.service.EvaluationDatasetVersionService;
import com.aether.evaluation.entity.EvaluationDataset;
import com.aether.evaluation.entity.EvaluationDatasetVersion;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.entity.AgentDefinition;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.evaluation.entity.EvaluationScore;
import com.aether.evaluation.entity.EvaluationBaseline;
import com.aether.evaluation.service.EvaluationBaselineService;
import com.aether.evaluation.service.EvaluationDataDeletionService;
import com.aether.evaluation.entity.EvaluationReview;
import com.aether.evaluation.service.EvaluationReviewService;
import com.aether.evaluation.service.impl.EvaluationExperimentAggregationService;
import com.aether.evaluation.entity.EvaluationTargetSnapshot;
import com.aether.permission.Permission;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.sys.service.AccountDataScopeService;
import com.aether.exception.ServerException;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONArray;

@Api(tags = "统一评测实验 API")
@RestController
@RequestMapping("/api/evaluation/experiments")
@Permission(path = "/evaluation/experiments")
public class EvaluationExperimentController {
    private final EvaluationExperimentService service;
    private final AccountDataScopeService dataScopeService;
    private final EvaluationResultService resultService; private final EvaluationBaselineService baselineService; private final EvaluationReviewService reviewService;
    private final EvaluationDataDeletionService deletionService;
    @Value("${aether.evaluation.enabled:true}") private boolean evaluationEnabled;
    private final EvaluationCaseVersionService caseVersionService; private final EvaluationTaskService taskService; private final EvaluationWorkerSlotService workerSlotService; private final EvaluationSnapshotService snapshotService; private final EvaluationExperimentAggregationService aggregationService; private final EvaluationScoreService scoreService; private final List<EvaluationTargetAdapter> adapters; private final EvaluationDatasetService datasetService; private final EvaluationDatasetVersionService datasetVersionService; private final AgentDefinitionService agentService; private final AgentWorkflowService workflowService;
    public EvaluationExperimentController(EvaluationExperimentService service, EvaluationResultService resultService, EvaluationBaselineService baselineService, EvaluationReviewService reviewService, EvaluationDataDeletionService deletionService, EvaluationCaseVersionService caseVersionService, EvaluationTaskService taskService, EvaluationWorkerSlotService workerSlotService, EvaluationSnapshotService snapshotService, EvaluationExperimentAggregationService aggregationService, EvaluationScoreService scoreService, @org.springframework.beans.factory.annotation.Autowired(required=false) List<EvaluationTargetAdapter> adapters, EvaluationDatasetService datasetService, EvaluationDatasetVersionService datasetVersionService, AgentDefinitionService agentService, AgentWorkflowService workflowService, AccountDataScopeService dataScopeService) { this.service = service; this.resultService = resultService; this.baselineService=baselineService; this.reviewService=reviewService; this.deletionService=deletionService; this.caseVersionService = caseVersionService; this.taskService = taskService; this.workerSlotService=workerSlotService; this.snapshotService = snapshotService; this.aggregationService=aggregationService; this.scoreService=scoreService; this.adapters=adapters;this.datasetService=datasetService;this.datasetVersionService=datasetVersionService;this.agentService=agentService;this.workflowService=workflowService; this.dataScopeService=dataScopeService; }

    @GetMapping
    public WebResponse<List<EvaluationExperiment>> list(@RequestParam(required = false) String targetType,
                                                         @RequestParam(required = false) String targetId,
                                                         @RequestParam(required = false) String creatorUserId) {
        return WebResponse.OK(service.lambdaQuery().eq(targetType != null, EvaluationExperiment::getTargetType, targetType)
                .eq(targetId != null, EvaluationExperiment::getTargetId, targetId)
                .in(dataScopeService != null, EvaluationExperiment::getCreatedBy, dataScopeService == null ? java.util.Collections.emptyList() : dataScopeService.readableCreatorIds(creatorUserId))
                .orderByDesc(EvaluationExperiment::getCreatedAt).list());
    }
    @GetMapping("/{id}")
    public WebResponse<?> detail(@PathVariable String id) {
        EvaluationExperiment experiment=required(id);
        return experiment==null?WebResponse.Error(404,I18nUtils.getMessage("agent.evaluation.experiment.not-found")):WebResponse.OK(experiment);
    }
    @DeleteMapping("/{id}")
    @Permission(path = "/evaluation/experiments", type = Permission.Type.Write)
    public WebResponse<?> delete(@PathVariable String id) {
        required(id);
        deletionService.deleteExperiment(id);
        return WebResponse.OK(I18nUtils.getMessage("agent.evaluation.data.delete.success"));
    }
    @PostMapping("/precheck")
    public WebResponse<?> precheck(@RequestBody EvaluationExperiment request) {
        java.util.Map<String,Object> out=new java.util.LinkedHashMap<>();
        if(request==null||blank(request.getTargetType())||blank(request.getTargetId())||blank(request.getDatasetVersionId())){out.put("ready",false);out.put("reasonCode","REQUIRED_FIELDS_MISSING");return WebResponse.OK(out);}
        boolean targetExists = targetReadable(request.getTargetType(), request.getTargetId());
        if(!targetExists){out.put("ready",false);out.put("reasonCode","TARGET_NOT_FOUND");return WebResponse.OK(out);}
        EvaluationDatasetVersion version=datasetVersionService.getById(request.getDatasetVersionId()); EvaluationDataset dataset=version==null?null:datasetService.getById(version.getDatasetId());
        if(version==null||dataset==null||!request.getTargetType().equals(dataset.getTargetType())){out.put("ready",false);out.put("reasonCode","DATASET_VERSION_MISMATCH");return WebResponse.OK(out);}
        if (dataScopeService != null) dataScopeService.assertReadable(dataset.getCreatedBy());
        JSONObject config; try{config=JSONObject.parseObject(request.getConfigJson());}catch(RuntimeException ex){config=null;}
        int repeats=config==null?0:config.getIntValue("repeats",0), parallelism=config==null?0:config.getIntValue("parallelism",0), timeout=config==null?0:config.getIntValue("caseTimeoutSeconds",0); java.math.BigDecimal minimumScore=config==null?null:config.getBigDecimal("minimumScore"), minimumPassRate=config==null?null:config.getBigDecimal("minimumPassRate");
        List<EvaluationCaseVersion> cases=caseVersionService.lambdaQuery().eq(EvaluationCaseVersion::getDatasetVersionId,version.getId()).eq(EvaluationCaseVersion::getDeleted,false).list();
        out.put("caseCount",cases.size());out.put("executionUnits",cases.size()*Math.max(repeats,0));out.put("parallelism",parallelism);out.put("caseTimeoutSeconds",timeout);
        boolean valid=repeats>=1&&repeats<=5&&parallelism>=1&&parallelism<=10&&timeout>=30&&timeout<=3600&&(minimumScore==null||inRange(minimumScore))&&(minimumPassRate==null||inRange(minimumPassRate))&& !cases.isEmpty();out.put("ready",valid);out.put("reasonCode",valid?"READY":cases.isEmpty()?"NO_CASES":"INVALID_EXECUTION_CONFIG");return WebResponse.OK(out);
    }

    @PostMapping
    @Permission(path = "/evaluation/experiments", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<String> create(@RequestBody EvaluationExperiment request) {
        if(!evaluationEnabled)return WebResponse.Error(503,I18nUtils.getMessage("agent.evaluation.disabled"));
        if (request == null || blank(request.getName()) || blank(request.getTargetType()) || blank(request.getTargetId())
                || blank(request.getSnapshotId()) || blank(request.getDatasetVersionId()) || blank(request.getSelectionJson()) || blank(request.getConfigJson()))
            return WebResponse.Error(422, I18nUtils.getMessage("agent.evaluation.experiment.required-fields"));
        if (!"AGENT".equals(request.getTargetType()) && !"WORKFLOW".equals(request.getTargetType()))
            return WebResponse.Error(422, I18nUtils.getMessage("agent.evaluation.target-type.invalid"));
        if (!targetReadable(request.getTargetType(), request.getTargetId()))
            return WebResponse.Error(404, I18nUtils.getMessage("agent.evaluation.target.not-found"));
        EvaluationTargetSnapshot snapshot = snapshotService.getById(request.getSnapshotId());
        if (snapshot == null) return WebResponse.Error(422, I18nUtils.getMessage("agent.evaluation.snapshot.not-found"));
        if (dataScopeService != null) dataScopeService.assertReadable(snapshot.getCreatedBy());
        if (!request.getTargetType().equals(snapshot.getTargetType()) || !request.getTargetId().equals(snapshot.getTargetId())) return WebResponse.Error(422, I18nUtils.getMessage("agent.evaluation.snapshot.target.mismatch"));
        EvaluationDatasetVersion datasetVersion = datasetVersionService.getById(request.getDatasetVersionId());
        EvaluationDataset dataset = datasetVersion == null ? null : datasetService.getById(datasetVersion.getDatasetId());
        if (datasetVersion == null || dataset == null || !request.getTargetType().equals(dataset.getTargetType()))
            return WebResponse.Error(422, I18nUtils.getMessage("agent.evaluation.dataset-version.mismatch"));
        if (dataScopeService != null) dataScopeService.assertReadable(dataset.getCreatedBy());
        JSONObject selection;
        JSONObject config;
        try { selection = JSONObject.parseObject(request.getSelectionJson()); config = JSONObject.parseObject(request.getConfigJson()); }
        catch (RuntimeException ex) { return WebResponse.Error(422, I18nUtils.getMessage("agent.evaluation.experiment.config.invalid")); }
        if (selection == null || config == null) return WebResponse.Error(422, I18nUtils.getMessage("agent.evaluation.experiment.config.invalid"));
        int repeats = config.getIntValue("repeats", 1);
        if (repeats < 1 || repeats > 5) return WebResponse.Error(422, I18nUtils.getMessage("agent.evaluation.experiment.repeats.invalid"));
        int parallelism = config.getIntValue("parallelism", 2), timeoutSeconds = config.getIntValue("caseTimeoutSeconds", 300);
        if (parallelism < 1 || parallelism > 10 || timeoutSeconds < 30 || timeoutSeconds > 3600) return WebResponse.Error(422, I18nUtils.getMessage("agent.evaluation.experiment.config.invalid"));
        java.math.BigDecimal minimumScore=config.getBigDecimal("minimumScore"), minimumPassRate=config.getBigDecimal("minimumPassRate");
        if (minimumScore == null) minimumScore = java.math.BigDecimal.valueOf(80);
        if (minimumPassRate == null) minimumPassRate = java.math.BigDecimal.valueOf(100);
        if (!inRange(minimumScore) || !inRange(minimumPassRate)) return WebResponse.Error(422, I18nUtils.getMessage("agent.evaluation.experiment.threshold.invalid"));
        Set<String> selectedCaseKeys = new HashSet<>();
        JSONArray caseKeys = selection.getJSONArray("caseKeys");
        if (caseKeys != null) for (Object caseKey : caseKeys) selectedCaseKeys.add(String.valueOf(caseKey));
        config.put("minimumScore", minimumScore);
        config.put("minimumPassRate", minimumPassRate);
        config.putIfAbsent("gradingProtocolVersion", 1);
        request.setConfigJson(config.toJSONString());
        request.setTargetFingerprint(snapshot.getFingerprint()); request.setConfigHash(sha256(request.getSelectionJson() + "\n" + request.getConfigJson())); request.setId(null); request.setScope("FULL"); request.setStatus("QUEUED"); request.setQualityStatus("PENDING"); request.setRevision(0L); request.setReportRevision(0L);
        List<EvaluationCaseVersion> cases = caseVersionService.lambdaQuery().eq(EvaluationCaseVersion::getDatasetVersionId, request.getDatasetVersionId()).eq(EvaluationCaseVersion::getDeleted, false).list();
        if (!selectedCaseKeys.isEmpty()) cases.removeIf(item -> !selectedCaseKeys.contains(item.getCaseKey()));
        if (cases.isEmpty()) return WebResponse.Error(422, I18nUtils.getMessage("agent.evaluation.experiment.cases.empty"));
        service.save(request);
        long now=System.currentTimeMillis(); for (EvaluationCaseVersion item : cases) for (int repeatIndex = 0; repeatIndex < repeats; repeatIndex++) { EvaluationResult result = new EvaluationResult(); result.setExperimentId(request.getId()); result.setCaseVersionId(item.getId()); result.setRepeatIndex(repeatIndex); result.setExecutionStatus("PENDING"); result.setGradingStatus("NOT_STARTED"); resultService.save(result); EvaluationTask task = new EvaluationTask(); task.setResultId(result.getId()); task.setPhase("EXECUTE"); task.setTargetType(request.getTargetType()); task.setTargetId(request.getTargetId()); task.setSnapshotId(request.getSnapshotId()); task.setStatus("READY"); task.setNextRunAt(now); task.setDeadlineAt(now+timeoutSeconds*1000L); taskService.save(task); }
        return WebResponse.OK(null, request.getId());
    }
    private boolean inRange(java.math.BigDecimal value) { return value.compareTo(java.math.BigDecimal.ZERO) >= 0 && value.compareTo(java.math.BigDecimal.valueOf(100)) <= 0; }

    private boolean targetReadable(String targetType, String targetId) {
        if ("AGENT".equals(targetType)) {
            AgentDefinition target = agentService.getById(targetId);
            if (target == null || Boolean.TRUE.equals(target.getDeleted())) return false;
            if (dataScopeService != null) dataScopeService.assertReadable(target.getCreatedBy());
            return true;
        }
        if ("WORKFLOW".equals(targetType)) {
            com.aether.workflow.entity.AgentWorkflow target = workflowService.getById(targetId);
            if (target == null || Boolean.TRUE.equals(target.getDeleted())) return false;
            if (dataScopeService != null) dataScopeService.assertReadable(target.getCreatedBy());
            return true;
        }
        return false;
    }
    @GetMapping("/{id}/progress")
    public WebResponse<?> progress(@PathVariable String id) {
        required(id);
        List<EvaluationResult> rows = resultService.lambdaQuery().eq(EvaluationResult::getExperimentId, id).list();
        java.util.Map<String,Object> out = new java.util.LinkedHashMap<>(); out.put("total", rows.size());
        out.put("queued", count(rows,"PENDING")); out.put("running", count(rows,"RUNNING")); out.put("succeeded", count(rows,"SUCCEEDED"));
        out.put("failed", count(rows,"FAILED")); out.put("blocked", count(rows,"BLOCKED")); out.put("timedOut", count(rows,"TIMED_OUT"));
        out.put("cancelled", count(rows,"CANCELLED")); out.put("finished", rows.stream().allMatch(this::finished)); return WebResponse.OK(out);
    }
    @GetMapping("/{id}/results")
    public WebResponse<List<EvaluationResult>> results(@PathVariable String id, @RequestParam(required=false) String executionStatus) {
        required(id);
        List<EvaluationResult> rows=resultService.lambdaQuery().eq(EvaluationResult::getExperimentId,id)
                .eq(executionStatus != null, EvaluationResult::getExecutionStatus, executionStatus).orderByAsc(EvaluationResult::getCreatedAt).list();
        // Large outputs and evidence are loaded by the detail endpoint, never by progress polling/table refreshes.
        for(EvaluationResult row:rows){row.setOutputJson(null);row.setEvidenceJson(null);row.setMetricsJson(null);}return WebResponse.OK(rows);
    }
    @GetMapping("/{id}/results/{resultId}")
    public WebResponse<?> resultDetail(@PathVariable String id,@PathVariable String resultId){required(id);EvaluationResult result=resultService.lambdaQuery().eq(EvaluationResult::getId,resultId).eq(EvaluationResult::getExperimentId,id).one();return result==null?WebResponse.Error(404,I18nUtils.getMessage("agent.evaluation.result.not-found")):WebResponse.OK(result);}
    /** Returns the immutable experiment context plus current result and scoring evidence. */
    @GetMapping("/{id}/export")
    public WebResponse<?> export(@PathVariable String id) {
        EvaluationExperiment experiment = required(id);
        List<EvaluationResult> results = resultService.lambdaQuery().eq(EvaluationResult::getExperimentId, id).orderByAsc(EvaluationResult::getCreatedAt).list();
        java.util.Map<String, EvaluationCaseVersion> cases = new java.util.LinkedHashMap<>();
        for (EvaluationCaseVersion item : caseVersionService.listByIds(results.stream().map(EvaluationResult::getCaseVersionId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet()))) cases.put(item.getId(), item);
        java.util.List<java.util.Map<String, Object>> units = new java.util.ArrayList<>();
        for (EvaluationResult result : results) {
            java.util.Map<String, Object> unit = new java.util.LinkedHashMap<>();
            EvaluationCaseVersion item = cases.get(result.getCaseVersionId());
            unit.put("caseKey", item == null ? null : item.getCaseKey()); unit.put("caseName", item == null ? null : item.getName()); unit.put("required", item != null && Boolean.TRUE.equals(item.getRequired()));
            unit.put("result", result); unit.put("scores", scoreService.lambdaQuery().eq(EvaluationScore::getResultId, result.getId()).orderByAsc(EvaluationScore::getCreatedAt).list()); units.add(unit);
        }
        java.util.Map<String, Object> report = new java.util.LinkedHashMap<>(); report.put("schemaVersion", 1); report.put("reportRevision", experiment.getReportRevision()); report.put("experiment", experiment); report.put("executionUnits", units);
        return WebResponse.OK(report);
    }
    @GetMapping("/{id}/results/{resultId}/scores")
    public WebResponse<?> scores(@PathVariable String id, @PathVariable String resultId) {
        required(id);
        EvaluationResult result = resultService.lambdaQuery().eq(EvaluationResult::getId, resultId).eq(EvaluationResult::getExperimentId, id).one();
        if (result == null) return WebResponse.Error(404, I18nUtils.getMessage("agent.evaluation.result.not-found"));
        return WebResponse.OK(scoreService.lambdaQuery().eq(EvaluationScore::getResultId, resultId).orderByAsc(EvaluationScore::getCreatedAt).list());
    }
    @GetMapping("/{id}/reviews")
    public WebResponse<?> reviews(@PathVariable String id, @RequestParam(required=false) String resultId) {
        required(id);
        return WebResponse.OK(reviewService.lambdaQuery().eq(EvaluationReview::getExperimentId,id).eq(resultId!=null,EvaluationReview::getResultId,resultId).eq(EvaluationReview::getDeleted,false).orderByDesc(EvaluationReview::getCreatedAt).list());
    }
    @PostMapping("/{id}/results/{resultId}/review")
    @Permission(path = "/evaluation/experiments", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<?> review(@PathVariable String id, @PathVariable String resultId, @RequestBody java.util.Map<String, Object> request) {
        EvaluationResult result = resultService.lambdaQuery().eq(EvaluationResult::getId, resultId).eq(EvaluationResult::getExperimentId, id).one();
        if (result == null) return WebResponse.Error(404, I18nUtils.getMessage("agent.evaluation.result.not-found"));
        String status = request == null || request.get("status") == null ? null : String.valueOf(request.get("status"));
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) return WebResponse.Error(422, I18nUtils.getMessage("agent.evaluation.review-status.invalid"));
        EvaluationExperiment experiment=required(id);
        EvaluationCaseVersion caseVersion=caseVersionService.getById(result.getCaseVersionId()); String caseKey=caseVersion==null?result.getCaseVersionId():caseVersion.getCaseKey();
        EvaluationReview previous=reviewService.lambdaQuery().eq(EvaluationReview::getExperimentId,id).eq(EvaluationReview::getCaseKey,caseKey).eq(EvaluationReview::getDeleted,false).orderByDesc(EvaluationReview::getCreatedAt).last("LIMIT 1").one();
        long nextRevision=(experiment.getReportRevision()==null?0:experiment.getReportRevision())+1; String reviewer=CurrentUser.getUser()==null?null:CurrentUser.getUser().get("userId"); String comment=request.get("comment")==null?null:String.valueOf(request.get("comment"));
        EvaluationReview review=new EvaluationReview(); review.setExperimentId(id);review.setCaseKey(caseKey);review.setResultId(resultId);review.setReviewerId(reviewer);review.setDecision(status);review.setReason(comment);review.setReportRevision(nextRevision);review.setPreviousReviewId(previous==null?null:previous.getId());reviewService.save(review);
        result.setReviewStatus(status); result.setReviewedBy(reviewer); result.setReviewComment(comment); result.setReviewedAt(System.currentTimeMillis()); resultService.updateById(result); experiment.setReportRevision(nextRevision);service.updateById(experiment); aggregationService.refresh(id); return WebResponse.OK(null);
    }    @PostMapping("/{id}/cancel")
    @Permission(path = "/evaluation/experiments", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<?> cancel(@PathVariable String id) {
        EvaluationExperiment experiment = required(id);
        if ("COMPLETED".equals(experiment.getStatus()) || "CANCELLED".equals(experiment.getStatus())) return WebResponse.OK(null);
        experiment.setStatus("CANCELLING"); experiment.setRevision((experiment.getRevision() == null ? 0 : experiment.getRevision()) + 1); service.updateById(experiment);
        List<EvaluationResult> results=resultService.lambdaQuery().eq(EvaluationResult::getExperimentId,id).list();
        for(EvaluationResult result:results){
            String executionStatus=result.getExecutionStatus();
            if("RUNNING".equals(executionStatus)) cancelDownstream(result,experiment);
            if("PENDING".equals(executionStatus)||"DISPATCHING".equals(executionStatus)||"RUNNING".equals(executionStatus)){
                // Cancellation is terminal locally even when an external Agent/Workflow callback is late or lost.
                // EvaluationResultCallbackService treats CANCELLED as immutable, so a late callback cannot revive it.
                result.setExecutionStatus("CANCELLED");result.setGradingStatus("SKIPPED");result.setQualityStatus("FAILED");resultService.updateById(result);
                EvaluationTask executionTask=taskService.getOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(EvaluationTask.class).eq(EvaluationTask::getResultId,result.getId()).eq(EvaluationTask::getPhase,"EXECUTE").eq(EvaluationTask::getDeleted,false),false);
                if(executionTask!=null)workerSlotService.releaseByTask(executionTask.getId());
            }
            taskService.lambdaUpdate().eq(EvaluationTask::getResultId,result.getId()).in(EvaluationTask::getStatus,"READY","CLAIMED").set(EvaluationTask::getStatus,"CANCELLED").update();
        }
        aggregationService.refresh(id);
        return WebResponse.OK(null);
    }
    /** Creates a separate RETRY_SUBSET experiment from terminal execution failures.  It never changes the source report. */
    @PostMapping("/{id}/reruns")
    @Permission(path = "/evaluation/experiments", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<?> rerunFailed(@PathVariable String id,@RequestBody(required=false) java.util.Map<String,Object> request) {
        if(!evaluationEnabled)return WebResponse.Error(503,I18nUtils.getMessage("agent.evaluation.disabled"));
        EvaluationExperiment source=required(id);
        Long expected=request==null?null:asLong(request.get("expectedReportRevision"));if(expected==null||!expected.equals(source.getReportRevision()))return WebResponse.Error(409,I18nUtils.getMessage("agent.evaluation.experiment.report-revision.conflict"));
        Set<String> selectedResultIds=new HashSet<>();if(request!=null&&request.get("resultIds") instanceof java.util.Collection<?> ids)for(Object value:ids)selectedResultIds.add(String.valueOf(value));
        List<EvaluationResult> failed=resultService.lambdaQuery().eq(EvaluationResult::getExperimentId,id).in(EvaluationResult::getExecutionStatus,"FAILED","BLOCKED","TIMED_OUT","CANCELLED","UNKNOWN").list();if(!selectedResultIds.isEmpty())failed.removeIf(result->!selectedResultIds.contains(result.getId()));
        Set<String> caseVersionIds=failed.stream().map(EvaluationResult::getCaseVersionId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());if(caseVersionIds.isEmpty())return WebResponse.Error(422,I18nUtils.getMessage("agent.evaluation.rerun.empty"));
        List<EvaluationCaseVersion> cases=caseVersionService.listByIds(caseVersionIds);JSONObject config;try{config=JSONObject.parseObject(source.getConfigJson());}catch(RuntimeException ex){return WebResponse.Error(422,I18nUtils.getMessage("agent.evaluation.experiment.config.invalid"));}if(config==null)return WebResponse.Error(422,I18nUtils.getMessage("agent.evaluation.experiment.config.invalid"));
        JSONArray keys=new JSONArray();for(EvaluationCaseVersion item:cases)keys.add(item.getCaseKey());JSONObject selection=new JSONObject();selection.put("mode","failed");selection.put("caseKeys",keys);
        EvaluationExperiment rerun=new EvaluationExperiment();rerun.setName(source.getName()+" (retry)");rerun.setTargetType(source.getTargetType());rerun.setTargetId(source.getTargetId());rerun.setSnapshotId(source.getSnapshotId());rerun.setTargetFingerprint(source.getTargetFingerprint());rerun.setDatasetVersionId(source.getDatasetVersionId());rerun.setSelectionJson(selection.toJSONString());rerun.setConfigJson(config.toJSONString());rerun.setConfigHash(sha256(rerun.getSelectionJson()+"\n"+rerun.getConfigJson()));rerun.setScope("RETRY_SUBSET");rerun.setStatus("QUEUED");rerun.setQualityStatus("PENDING");rerun.setParentExperimentId(source.getId());rerun.setRevision(0L);rerun.setReportRevision(0L);service.save(rerun);
        int repeats=config.getIntValue("repeats",1),timeoutSeconds=config.getIntValue("caseTimeoutSeconds",300);long now=System.currentTimeMillis();for(EvaluationCaseVersion item:cases)for(int repeatIndex=0;repeatIndex<repeats;repeatIndex++){EvaluationResult result=new EvaluationResult();result.setExperimentId(rerun.getId());result.setCaseVersionId(item.getId());result.setRepeatIndex(repeatIndex);result.setExecutionStatus("PENDING");result.setGradingStatus("NOT_STARTED");resultService.save(result);EvaluationTask task=new EvaluationTask();task.setResultId(result.getId());task.setPhase("EXECUTE");task.setTargetType(rerun.getTargetType());task.setTargetId(rerun.getTargetId());task.setSnapshotId(rerun.getSnapshotId());task.setStatus("READY");task.setNextRunAt(now);task.setDeadlineAt(now+timeoutSeconds*1000L);taskService.save(task);}return WebResponse.OK(null,rerun.getId());
    }
    /** Regrades only successful execution units; it never dispatches the target again. */
    @PostMapping("/{id}/grading-retries")
    @Permission(path = "/evaluation/experiments", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<?> retryGrading(@PathVariable String id, @RequestBody(required=false) java.util.Map<String,Object> request) {
        if(!evaluationEnabled)return WebResponse.Error(503,I18nUtils.getMessage("agent.evaluation.disabled"));
        EvaluationExperiment experiment=required(id);
        Long expected=request==null?null:asLong(request.get("expectedReportRevision"));if(expected==null||!expected.equals(experiment.getReportRevision()))return WebResponse.Error(409,I18nUtils.getMessage("agent.evaluation.experiment.report-revision.conflict"));
        Set<String> requested=new HashSet<>();if(request!=null&&request.get("resultIds") instanceof java.util.Collection<?> ids)for(Object value:ids)requested.add(String.valueOf(value));
        List<EvaluationResult> results=resultService.lambdaQuery().eq(EvaluationResult::getExperimentId,id).eq(EvaluationResult::getExecutionStatus,"SUCCEEDED").eq(EvaluationResult::getGradingStatus,"FAILED").list();if(!requested.isEmpty())results.removeIf(item->!requested.contains(item.getId()));
        if(results.isEmpty())return WebResponse.Error(422,I18nUtils.getMessage("agent.evaluation.grading-retry.empty"));
        long now=System.currentTimeMillis();for(EvaluationResult result:results){int round=(result.getActiveGradingRound()==null?0:result.getActiveGradingRound())+1;result.setActiveGradingRound(round);result.setGradingStatus("PENDING");result.setQualityStatus("INCOMPLETE");result.setErrorCode(null);result.setErrorMessage(null);resultService.updateById(result);EvaluationTask task=new EvaluationTask();task.setResultId(result.getId());task.setPhase("GRADE");task.setGradingRound(round);task.setStatus("READY");task.setNextRunAt(now);taskService.save(task);}
        experiment.setQualityStatus("INCOMPLETE");experiment.setReportRevision((experiment.getReportRevision()==null?0:experiment.getReportRevision())+1);experiment.setRevision((experiment.getRevision()==null?0:experiment.getRevision())+1);service.updateById(experiment);return WebResponse.OK(results.size());
    }
    @PutMapping("/baselines")
    @Permission(path = "/evaluation/experiments", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<?> setBaseline(@RequestBody java.util.Map<String,Object> request) {
        String experimentId=request==null?null:String.valueOf(request.get("experimentId"));EvaluationExperiment experiment=required(experimentId);
        if(!"COMPLETED".equals(experiment.getStatus())||"INCOMPLETE".equals(experiment.getQualityStatus()))return WebResponse.Error(422,I18nUtils.getMessage("agent.evaluation.baseline.experiment.incomplete"));
        String owner=CurrentUser.userId();EvaluationBaseline existing=baselineService.getOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(EvaluationBaseline.class).eq(EvaluationBaseline::getCreatedBy,owner).eq(EvaluationBaseline::getTargetType,experiment.getTargetType()).eq(EvaluationBaseline::getTargetId,experiment.getTargetId()).eq(EvaluationBaseline::getDatasetVersionId,experiment.getDatasetVersionId()).eq(EvaluationBaseline::getConfigHash,experiment.getConfigHash()).eq(EvaluationBaseline::getDeleted,false),false);
        if(existing==null){existing=new EvaluationBaseline();existing.setTargetType(experiment.getTargetType());existing.setTargetId(experiment.getTargetId());existing.setDatasetVersionId(experiment.getDatasetVersionId());existing.setConfigHash(experiment.getConfigHash());existing.setRevision(0L);}existing.setExperimentId(experimentId);existing.setRevision((existing.getRevision()==null?0:existing.getRevision())+1);baselineService.saveOrUpdate(existing);return WebResponse.OK(existing);
    }
    /** Lists the caller's baseline records.  The client uses this to select only a compatible baseline before comparison. */
    @GetMapping("/baselines")
    public WebResponse<List<EvaluationBaseline>> baselines(@RequestParam(required=false) String targetType,@RequestParam(required=false) String targetId,@RequestParam(required=false) String datasetVersionId,@RequestParam(required=false) String configHash,@RequestParam(required=false) String creatorUserId) {
        String owner=CurrentUser.userId();
        return WebResponse.OK(baselineService.lambdaQuery().in(EvaluationBaseline::getCreatedBy, dataScopeService == null ? java.util.Collections.singletonList(owner) : dataScopeService.readableCreatorIds(creatorUserId)).eq(targetType!=null,EvaluationBaseline::getTargetType,targetType).eq(targetId!=null,EvaluationBaseline::getTargetId,targetId).eq(datasetVersionId!=null,EvaluationBaseline::getDatasetVersionId,datasetVersionId).eq(configHash!=null,EvaluationBaseline::getConfigHash,configHash).eq(EvaluationBaseline::getDeleted,false).orderByDesc(EvaluationBaseline::getUpdatedAt).list());
    }
    @GetMapping("/comparisons")
    public WebResponse<?> compare(@RequestParam String baselineId,@RequestParam String candidateId) {
        EvaluationBaseline baseline=baselineService.getById(baselineId);EvaluationExperiment candidate=service.getById(candidateId);EvaluationExperiment source=baseline==null?null:service.getById(baseline.getExperimentId());if(baseline==null||candidate==null||source==null)return WebResponse.Error(404,I18nUtils.getMessage("agent.evaluation.comparison.not-found"));if(dataScopeService!=null){dataScopeService.assertReadable(baseline.getCreatedBy());dataScopeService.assertReadable(candidate.getCreatedBy());dataScopeService.assertReadable(source.getCreatedBy());}
        boolean comparable=Objects.equals(source.getTargetType(),candidate.getTargetType())&&Objects.equals(source.getTargetId(),candidate.getTargetId())&&Objects.equals(source.getDatasetVersionId(),candidate.getDatasetVersionId())&&Objects.equals(source.getConfigHash(),candidate.getConfigHash());java.util.Map<String,Object> out=new java.util.LinkedHashMap<>();out.put("comparable",comparable);out.put("baselineExperimentId",source.getId());out.put("candidateExperimentId",candidate.getId());if(!comparable){out.put("reasonCode","CONFIGURATION_MISMATCH");return WebResponse.OK(out);}out.put("reasonCode","COMPARABLE");out.put("baselineMetrics",source.getMetricsJson());out.put("candidateMetrics",candidate.getMetricsJson());out.put("caseDifferences",compareCases(source.getId(),candidate.getId()));return WebResponse.OK(out);
    }
    private EvaluationExperiment required(String id) {
        EvaluationExperiment value = service.getById(id);
        if (value == null || Boolean.TRUE.equals(value.getDeleted())) throw new ServerException(404, I18nUtils.getMessage("agent.evaluation.experiment.not-found"));
        if (dataScopeService != null) dataScopeService.assertReadable(value.getCreatedBy());
        return value;
    }
    private long count(List<EvaluationResult> rows, String status) { return rows.stream().filter(r -> status.equals(r.getExecutionStatus())).count(); }
    private boolean finished(EvaluationResult r) { String s=r.getExecutionStatus(); return "SUCCEEDED".equals(s)||"FAILED".equals(s)||"BLOCKED".equals(s)||"TIMED_OUT".equals(s)||"CANCELLED".equals(s); }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private Long asLong(Object value){try{return value==null?null:Long.valueOf(String.valueOf(value));}catch(NumberFormatException ex){return null;}}
    private java.util.List<java.util.Map<String,Object>> compareCases(String baselineExperimentId,String candidateExperimentId){java.util.Map<String,BigDecimal> baseline=caseScores(baselineExperimentId),candidate=caseScores(candidateExperimentId);java.util.List<java.util.Map<String,Object>> out=new java.util.ArrayList<>();for(String key:candidate.keySet()){if(!baseline.containsKey(key))continue;java.util.Map<String,Object> row=new java.util.LinkedHashMap<>();row.put("caseKey",key);row.put("baselineScore",baseline.get(key));row.put("candidateScore",candidate.get(key));row.put("scoreDelta",candidate.get(key)==null||baseline.get(key)==null?null:candidate.get(key).subtract(baseline.get(key)));out.add(row);}return out;}
    private java.util.Map<String,BigDecimal> caseScores(String experimentId){java.util.List<EvaluationResult> rows=resultService.lambdaQuery().eq(EvaluationResult::getExperimentId,experimentId).list();java.util.Map<String,EvaluationCaseVersion> cases=caseVersionService.listByIds(rows.stream().map(EvaluationResult::getCaseVersionId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet())).stream().collect(java.util.stream.Collectors.toMap(EvaluationCaseVersion::getId,item->item));java.util.Map<String,java.util.List<EvaluationResult>> grouped=rows.stream().collect(java.util.stream.Collectors.groupingBy(EvaluationResult::getCaseVersionId));java.util.Map<String,BigDecimal> out=new java.util.LinkedHashMap<>();for(java.util.Map.Entry<String,java.util.List<EvaluationResult>> entry:grouped.entrySet()){EvaluationCaseVersion item=cases.get(entry.getKey());if(item==null||entry.getValue().stream().anyMatch(result->result.getScore()==null)){out.put(item==null?entry.getKey():item.getCaseKey(),null);continue;}BigDecimal sum=entry.getValue().stream().map(EvaluationResult::getScore).reduce(BigDecimal.ZERO,BigDecimal::add);out.put(item.getCaseKey(),sum.divide(BigDecimal.valueOf(entry.getValue().size()),2,java.math.RoundingMode.HALF_UP));}return out;}
    private void cancelDownstream(EvaluationResult result, EvaluationExperiment experiment) { if(adapters==null)return; EvaluationTask task=new EvaluationTask();task.setPhase("EXECUTE");task.setTargetType(experiment.getTargetType());adapters.stream().filter(item->item.supports(task)).findFirst().ifPresent(item->item.cancel(result.getId())); }
    private String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format("%02x", b));
            return result.toString();
        } catch (Exception ex) { throw new IllegalStateException(I18nUtils.getMessage("agent.evaluation.hash.failed"), ex); }
    }
}
