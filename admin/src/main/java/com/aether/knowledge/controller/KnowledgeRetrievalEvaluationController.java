package com.aether.knowledge.controller;

import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.knowledge.model.KnowledgeRetrievalEvaluationCase;
import com.aether.knowledge.model.KnowledgeRetrievalEvaluationReport;
import com.aether.knowledge.service.KnowledgeRetrievalEvaluationService;
import com.aether.knowledge.entity.KnowledgeRetrievalEvaluationSet;
import com.aether.knowledge.entity.KnowledgeRetrievalEvaluationCaseEntity;
import com.aether.knowledge.mapper.KnowledgeRetrievalEvaluationSetMapper;
import com.aether.knowledge.mapper.KnowledgeRetrievalEvaluationCaseMapper;
import com.aether.knowledge.mapper.KnowledgeRetrievalEvaluationRunMapper;
import com.aether.knowledge.mapper.KnowledgeRetrievalEvaluationResultMapper;
import com.aether.knowledge.mapper.KnowledgeRetrievalEvaluationTaskMapper;
import com.aether.knowledge.mapper.KnowledgeRetrievalEvaluationLabelMapper;
import com.aether.knowledge.mapper.KnowledgeRetrievalEvaluationSetVersionMapper;
import com.aether.knowledge.entity.KnowledgeRetrievalEvaluationRun;
import com.aether.knowledge.entity.KnowledgeRetrievalEvaluationResult;
import com.aether.knowledge.entity.KnowledgeRetrievalEvaluationTask;
import com.aether.knowledge.entity.KnowledgeRetrievalEvaluationLabel;
import com.aether.knowledge.entity.KnowledgeRetrievalEvaluationSetVersion;
import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.knowledge.service.KnowledgeDocumentChunkService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeBaseService;
import com.aether.knowledge.service.KnowledgeAccessService;
import com.aether.agent.entity.AgentKnowledgeBaseBinding;
import com.aether.agent.entity.ModelCatalog;
import com.aether.agent.service.AgentKnowledgeBaseBindingService;
import com.aether.agent.service.ModelCatalogService;
import com.aether.knowledge.vo.KnowledgeRetrievalEvaluationResultVo;
import com.aether.knowledge.vo.KnowledgeRetrievalEvaluationComparisonVo;
import com.aether.knowledge.vo.KnowledgeRetrievalEvaluationHealthVo;
import com.aether.knowledge.vo.KnowledgeRetrievalEvaluationCaseTransferVo;
import com.aether.knowledge.vo.KnowledgeRetrievalEvaluationImportPreviewVo;
import com.aether.knowledge.vo.KnowledgeRetrievalEvaluationWorkbenchVo;
import com.aether.knowledge.service.impl.KnowledgeRetrievalEvaluationTaskWorker;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aether.permission.Permission;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * 提供知识库RetrievalEvaluation相关的 REST 接口。
 */
@Api(tags = "知识库检索评测 API")
@RestController
@Permission(path = "/knowledge/evaluation")
@RequestMapping("/api/knowledge/evaluation")
public class KnowledgeRetrievalEvaluationController {
    private final KnowledgeRetrievalEvaluationService evaluationService;
    private final KnowledgeRetrievalEvaluationSetMapper setMapper;
    private final KnowledgeRetrievalEvaluationCaseMapper caseMapper;
    private final KnowledgeRetrievalEvaluationRunMapper runMapper;
    private final KnowledgeRetrievalEvaluationResultMapper resultMapper;
    private final KnowledgeRetrievalEvaluationTaskMapper taskMapper;
    private final KnowledgeRetrievalEvaluationLabelMapper labelMapper;
    private final KnowledgeRetrievalEvaluationSetVersionMapper setVersionMapper;
    private final KnowledgeRetrievalEvaluationTaskWorker taskWorker;
    private final KnowledgeDocumentChunkService chunkService;
    private final KnowledgeDocumentService documentService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeAccessService knowledgeAccessService;
    private final AgentKnowledgeBaseBindingService bindingService;
    private final ModelCatalogService modelCatalogService;

    /**
     * 创建 {@code KnowledgeRetrievalEvaluationController} 实例。
     */
    public KnowledgeRetrievalEvaluationController(KnowledgeRetrievalEvaluationService evaluationService,
                                                  KnowledgeRetrievalEvaluationSetMapper setMapper,
                                                  KnowledgeRetrievalEvaluationCaseMapper caseMapper,
                                                  KnowledgeRetrievalEvaluationRunMapper runMapper,
                                                  KnowledgeRetrievalEvaluationResultMapper resultMapper,
                                                  KnowledgeRetrievalEvaluationTaskMapper taskMapper,
                                                  KnowledgeRetrievalEvaluationLabelMapper labelMapper,
                                                  KnowledgeRetrievalEvaluationSetVersionMapper setVersionMapper,
                                                  KnowledgeRetrievalEvaluationTaskWorker taskWorker,
                                                  KnowledgeDocumentChunkService chunkService,
                                                  KnowledgeDocumentService documentService,
                                                  KnowledgeBaseService knowledgeBaseService,
                                                  KnowledgeAccessService knowledgeAccessService,
                                                  AgentKnowledgeBaseBindingService bindingService,
                                                  ModelCatalogService modelCatalogService) {
        this.evaluationService = evaluationService;
        this.setMapper = setMapper;
        this.caseMapper = caseMapper;
        this.runMapper = runMapper;
        this.resultMapper = resultMapper;
        this.taskMapper = taskMapper;
        this.labelMapper = labelMapper;
        this.setVersionMapper = setVersionMapper;
        this.taskWorker = taskWorker;
        this.chunkService = chunkService;
        this.documentService = documentService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeAccessService = knowledgeAccessService;
        this.bindingService = bindingService;
        this.modelCatalogService = modelCatalogService;
    }

    /**
     * Creates a durable background run and returns immediately with its identifier.
     */
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write)
    @PostMapping("/sets/{id}/runs")
    public WebResponse<String> createRun(@PathVariable String id, @RequestParam(value = "evaluationSetVersionId", required = false) String evaluationSetVersionId) {
        KnowledgeRetrievalEvaluationSet set = setMapper.selectById(id);
        if (set == null || Boolean.TRUE.equals(set.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("knowledge.evaluation.set.not-found"));
        if (runMapper.selectCount(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationRun.class)
                .eq(KnowledgeRetrievalEvaluationRun::getEvaluationSetId, id)
                .eq(KnowledgeRetrievalEvaluationRun::getDeleted, false)
                .eq(KnowledgeRetrievalEvaluationRun::getStatus, "RUNNING")) > 0)
            throw new ServerException(409, I18nUtils.getMessage("knowledge.evaluation.run.already-running"));
        if (StringUtils.isBlank(evaluationSetVersionId)) requireHealthy(id);
        List<KnowledgeRetrievalEvaluationCaseEntity> cases = caseMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationCaseEntity.class).eq(KnowledgeRetrievalEvaluationCaseEntity::getEvaluationSetId, id).eq(KnowledgeRetrievalEvaluationCaseEntity::getDeleted, false).eq(KnowledgeRetrievalEvaluationCaseEntity::getStatus, 1));
        List<KnowledgeRetrievalEvaluationLabel> runLabels;
        String datasetSnapshot;
        if (StringUtils.isBlank(evaluationSetVersionId)) {
            Set<String> caseIds = cases.stream().map(KnowledgeRetrievalEvaluationCaseEntity::getId).collect(Collectors.toSet());
            runLabels = caseIds.isEmpty() ? Collections.emptyList() : labelMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationLabel.class).in(KnowledgeRetrievalEvaluationLabel::getEvaluationCaseId, caseIds).eq(KnowledgeRetrievalEvaluationLabel::getDeleted, false).eq(KnowledgeRetrievalEvaluationLabel::getStatus, 1));
            datasetSnapshot = JSON.toJSONString(cases);
        } else {
            KnowledgeRetrievalEvaluationSetVersion version = setVersionMapper.selectById(evaluationSetVersionId);
            if (version == null || Boolean.TRUE.equals(version.getDeleted()) || !id.equals(version.getEvaluationSetId()))
                throw new ServerException(404, I18nUtils.getMessage("knowledge.evaluation.set-version.not-found"));
            com.alibaba.fastjson2.JSONObject snapshot = JSON.parseObject(version.getSnapshotJson());
            cases = snapshot.getList("cases", KnowledgeRetrievalEvaluationCaseEntity.class);
            runLabels = snapshot.getList("labels", KnowledgeRetrievalEvaluationLabel.class);
            datasetSnapshot = version.getSnapshotJson();
        }
        KnowledgeRetrievalEvaluationRun run = new KnowledgeRetrievalEvaluationRun();
        run.setEvaluationSetId(id);
        run.setEvaluationSetVersionId(evaluationSetVersionId);
        run.setAgentDefinitionIdSnapshot(set.getAgentDefinitionId());
        run.setRetrievalConfigSnapshot(retrievalConfigSnapshot(set.getAgentDefinitionId(), cases, runLabels));
        run.setStatus("RUNNING");
        run.setDatasetSnapshot(datasetSnapshot);
        run.setRunConfigSnapshot(JSON.toJSONString(Collections.singletonMap("executionMode", "ASYNCHRONOUS")));
        run.setTotalCount(0);
        run.setInvalidCount(0);
        run.setFailedCount(0);
        run.setStartedAt(System.currentTimeMillis());
        runMapper.insert(run);
        int invalid = 0;
        for (KnowledgeRetrievalEvaluationCaseEntity item : cases) {
            List<KnowledgeRetrievalEvaluationLabel> labels = runLabels.stream().filter(label -> item.getId().equals(label.getEvaluationCaseId())).collect(Collectors.toList());
            String targetType = labels.isEmpty() ? StringUtils.upperCase(StringUtils.defaultIfBlank(item.getTargetType(), StringUtils.isNotBlank(item.getSectionPath()) ? "SECTION" : "DOCUMENT")) : StringUtils.upperCase(labels.get(0).getTargetType());
            List<KnowledgeDocumentChunk> chunks;
            if (StringUtils.isBlank(item.getQuestion()) || !"DOCUMENT".equals(targetType) && !"SECTION".equals(targetType) && !"CHUNK".equals(targetType))
                chunks = Collections.emptyList();
            else if (!labels.isEmpty()) {
                chunks = new ArrayList<>();
                for (KnowledgeRetrievalEvaluationLabel label : labels) {
                    List<KnowledgeDocumentChunk> resolved = resolveTargetChunks(label.getTargetType(), label.getDocumentId(), label.getSectionPath(), label.getChunkId());
                    if (!targetType.equals(StringUtils.upperCase(label.getTargetType())) || resolved.isEmpty()) {
                        chunks = Collections.emptyList();
                        break;
                    }
                    chunks.addAll(resolved);
                }
            } else if ("CHUNK".equals(targetType))
                chunks = StringUtils.isBlank(item.getChunkId()) ? Collections.<KnowledgeDocumentChunk>emptyList() : chunkService.list(Wrappers.lambdaQuery(KnowledgeDocumentChunk.class).eq(KnowledgeDocumentChunk::getId, item.getChunkId()).eq(KnowledgeDocumentChunk::getDeleted, false).eq(KnowledgeDocumentChunk::getDocumentId, item.getDocumentId()));
            else
                chunks = StringUtils.isBlank(item.getDocumentId()) ? Collections.<KnowledgeDocumentChunk>emptyList() : chunkService.list(Wrappers.lambdaQuery(KnowledgeDocumentChunk.class).eq(KnowledgeDocumentChunk::getDocumentId, item.getDocumentId()).eq(KnowledgeDocumentChunk::getDeleted, false).eq("SECTION".equals(targetType), KnowledgeDocumentChunk::getSectionPath, item.getSectionPath()));
            if (chunks.isEmpty()) {
                invalid++;
                KnowledgeRetrievalEvaluationResult result = new KnowledgeRetrievalEvaluationResult();
                result.setRunId(run.getId());
                result.setEvaluationCaseId(item.getId());
                result.setStatus("INVALID_LABEL");
                result.setQuestionSnapshot(item.getQuestion());
                result.setExpectedDocumentIdSnapshot(item.getDocumentId());
                result.setExpectedSectionPathSnapshot(item.getSectionPath());
                result.setTargetTypeSnapshot(targetType);
                result.setRetrievedChunkIds("[]");
                result.setErrorCode("TARGET_NOT_RESOLVED");
                result.setErrorMessage(I18nUtils.getMessage("knowledge.evaluation.error.target-not-resolved"));
                resultMapper.insert(result);
                continue;
            }
            KnowledgeRetrievalEvaluationTask task = new KnowledgeRetrievalEvaluationTask();
            task.setRunId(run.getId());
            task.setEvaluationCaseId(item.getId());
            task.setQuestionSnapshot(item.getQuestion());
            task.setTargetTypeSnapshot(targetType);
            task.setExpectedChunkIdsSnapshot(JSON.toJSONString(chunks.stream().map(KnowledgeDocumentChunk::getId).distinct().collect(Collectors.toList())));
            task.setExpectedDocumentIdSnapshot(labels.isEmpty() ? item.getDocumentId() : null);
            task.setExpectedSectionPathSnapshot(labels.isEmpty() ? item.getSectionPath() : null);
            task.setStatus("QUEUED");
            task.setAttemptCount(0);
            task.setMaxAttempts(3);
            taskMapper.insert(task);
        }
        if (invalid > 0) {
            run.setInvalidCount(invalid);
            runMapper.updateById(run);
        }
        taskWorker.updateRun(run.getId());
        taskWorker.dispatch(run.getId());
        return WebResponse.OK(I18nUtils.getMessage("knowledge.evaluation.run.started"), run.getId());
    }

    /**
     * Returns counters suitable for short-interval polling while a run is active.
     */
    @GetMapping("/sets/{setId}/runs/{runId}/progress")
    public WebResponse<Map<String, Object>> progress(@PathVariable String setId, @PathVariable String runId) {
        KnowledgeRetrievalEvaluationRun run = runMapper.selectById(runId);
        if (run == null || Boolean.TRUE.equals(run.getDeleted()) || !setId.equals(run.getEvaluationSetId()))
            throw new ServerException(404, I18nUtils.getMessage("knowledge.evaluation.run.not-found"));
        List<KnowledgeRetrievalEvaluationTask> tasks = taskMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationTask.class).eq(KnowledgeRetrievalEvaluationTask::getRunId, runId).eq(KnowledgeRetrievalEvaluationTask::getDeleted, false));
        int queued = 0, running = 0, succeeded = 0, failed = 0, cancelled = 0;
        for (KnowledgeRetrievalEvaluationTask task : tasks) {
            if ("QUEUED".equals(task.getStatus())) queued++;
            else if ("RUNNING".equals(task.getStatus())) running++;
            else if ("SUCCEEDED".equals(task.getStatus())) succeeded++;
            else if ("FAILED".equals(task.getStatus())) failed++;
            else if ("CANCELLED".equals(task.getStatus())) cancelled++;
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("runId", runId);
        response.put("status", run.getStatus());
        response.put("total", tasks.size() + run.getInvalidCount());
        response.put("invalid", run.getInvalidCount());
        response.put("queued", queued);
        response.put("running", running);
        response.put("succeeded", succeeded);
        response.put("failed", failed);
        response.put("cancelled", cancelled);
        response.put("finished", run.getFinishedAt() != null);
        return WebResponse.OK(response);
    }

    /**
     * Stops unclaimed tasks. A running retrieval may finish, but no further tasks will be dispatched.
     */
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write)
    @PostMapping("/sets/{setId}/runs/{runId}/cancel")
    public WebResponse<Void> cancelRun(@PathVariable String setId, @PathVariable String runId) {
        KnowledgeRetrievalEvaluationRun run = runMapper.selectById(runId);
        if (run == null || Boolean.TRUE.equals(run.getDeleted()) || !setId.equals(run.getEvaluationSetId()))
            throw new ServerException(404, I18nUtils.getMessage("knowledge.evaluation.run.not-found"));
        if ("RUNNING".equals(run.getStatus())) {
            run.setStatus("CANCELLED");
            run.setFinishedAt(System.currentTimeMillis());
            runMapper.updateById(run);
            taskMapper.update(null, Wrappers.lambdaUpdate(KnowledgeRetrievalEvaluationTask.class).eq(KnowledgeRetrievalEvaluationTask::getRunId, runId).eq(KnowledgeRetrievalEvaluationTask::getStatus, "QUEUED").set(KnowledgeRetrievalEvaluationTask::getStatus, "CANCELLED").set(KnowledgeRetrievalEvaluationTask::getFinishedAt, System.currentTimeMillis()));
        }
        return WebResponse.OK(I18nUtils.getMessage("knowledge.evaluation.run.cancelled"));
    }

    /**
     * Requeues terminal retrieval failures without rerunning successful or invalid cases.
     */
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write)
    @PostMapping("/sets/{setId}/runs/{runId}/retry-failed")
    public WebResponse<Void> retryFailed(@PathVariable String setId, @PathVariable String runId) {
        KnowledgeRetrievalEvaluationRun run = runMapper.selectById(runId);
        if (run == null || Boolean.TRUE.equals(run.getDeleted()) || !setId.equals(run.getEvaluationSetId()))
            throw new ServerException(404, I18nUtils.getMessage("knowledge.evaluation.run.not-found"));
        int requeued = taskMapper.update(null, Wrappers.lambdaUpdate(KnowledgeRetrievalEvaluationTask.class).eq(KnowledgeRetrievalEvaluationTask::getRunId, runId).eq(KnowledgeRetrievalEvaluationTask::getStatus, "FAILED").set(KnowledgeRetrievalEvaluationTask::getStatus, "QUEUED").set(KnowledgeRetrievalEvaluationTask::getAttemptCount, 0).set(KnowledgeRetrievalEvaluationTask::getErrorCode, null).set(KnowledgeRetrievalEvaluationTask::getErrorMessage, null).set(KnowledgeRetrievalEvaluationTask::getFinishedAt, null));
        if (requeued > 0) {
            resultMapper.delete(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationResult.class).eq(KnowledgeRetrievalEvaluationResult::getRunId, runId).eq(KnowledgeRetrievalEvaluationResult::getStatus, "RETRIEVAL_ERROR"));
            run.setStatus("RUNNING");
            run.setFinishedAt(null);
            run.setFailedCount(0);
            runMapper.updateById(run);
            taskWorker.dispatch(runId);
        }
        return WebResponse.OK(I18nUtils.getMessage("knowledge.evaluation.run.failed-items.retried"));
    }

    /**
     * 处理labels。
     */
    @GetMapping("/sets/{setId}/cases/{caseId}/labels")
    public WebResponse<List<KnowledgeRetrievalEvaluationLabel>> labels(@PathVariable String setId, @PathVariable String caseId) {
        requireCase(setId, caseId);
        return WebResponse.OK(labelMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationLabel.class).eq(KnowledgeRetrievalEvaluationLabel::getEvaluationCaseId, caseId).eq(KnowledgeRetrievalEvaluationLabel::getDeleted, false).orderByAsc(KnowledgeRetrievalEvaluationLabel::getCreatedAt)));
    }

    /**
     * 保存Label。
     */
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write)
    @PostMapping("/sets/{setId}/cases/{caseId}/labels")
    public WebResponse<String> saveLabel(@PathVariable String setId, @PathVariable String caseId, @RequestBody KnowledgeRetrievalEvaluationLabel label) {
        requireCase(setId, caseId);
        String targetType = StringUtils.upperCase(label.getTargetType());
        if (!"DOCUMENT".equals(targetType) && !"SECTION".equals(targetType) && !"CHUNK".equals(targetType))
            throw new ServerException(400, I18nUtils.getMessage("knowledge.evaluation.label.target-type.invalid"));
        if (StringUtils.isBlank(label.getDocumentId()) || "SECTION".equals(targetType) && StringUtils.isBlank(label.getSectionPath()) || "CHUNK".equals(targetType) && StringUtils.isBlank(label.getChunkId()))
            throw new ServerException(400, I18nUtils.getMessage("knowledge.evaluation.label.target.required"));
        List<KnowledgeRetrievalEvaluationLabel> existing = labelMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationLabel.class).eq(KnowledgeRetrievalEvaluationLabel::getEvaluationCaseId, caseId).eq(KnowledgeRetrievalEvaluationLabel::getDeleted, false));
        if (!existing.isEmpty() && !targetType.equals(StringUtils.upperCase(existing.get(0).getTargetType())))
            throw new ServerException(409, I18nUtils.getMessage("knowledge.evaluation.label.target-type.inconsistent"));
        label.setEvaluationCaseId(caseId);
        label.setTargetType(targetType);
        if (label.getRelevanceGrade() == null) label.setRelevanceGrade(1);
        if (label.getRelevanceGrade() < 1 || label.getRelevanceGrade() > 3)
            throw new ServerException(400, I18nUtils.getMessage("knowledge.evaluation.label.relevance-grade.invalid"));
        if (label.getIsRequired() == null) label.setIsRequired(true);
        if (label.getStatus() == null) label.setStatus(1);
        labelMapper.insert(label);
        return WebResponse.OK(I18nUtils.getMessage("knowledge.evaluation.label.create.success"), label.getId());
    }

    /**
     * 删除Label。
     */
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write)
    @DeleteMapping("/sets/{setId}/cases/{caseId}/labels/{labelId}")
    public WebResponse<Void> deleteLabel(@PathVariable String setId, @PathVariable String caseId, @PathVariable String labelId) {
        requireCase(setId, caseId);
        KnowledgeRetrievalEvaluationLabel label = labelMapper.selectById(labelId);
        if (label == null || Boolean.TRUE.equals(label.getDeleted()) || !caseId.equals(label.getEvaluationCaseId()))
            throw new ServerException(404, I18nUtils.getMessage("knowledge.evaluation.label.not-found"));
        label.setDeleted(true);
        labelMapper.updateById(label);
        clearLegacyTargetWhenNoLabels(caseId);
        return WebResponse.OK(I18nUtils.getMessage("knowledge.evaluation.label.delete.success"));
    }

    /**
     * 处理batch删除Labels。
     */
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write)
    @PostMapping("/sets/{setId}/cases/{caseId}/labels/batch-delete")
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<Void> batchDeleteLabels(@PathVariable String setId, @PathVariable String caseId,
                                               @RequestBody IdsRequest request) {
        requireCase(setId, caseId);
        requireIds(request);
        labelMapper.delete(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationLabel.class)
                .eq(KnowledgeRetrievalEvaluationLabel::getEvaluationCaseId, caseId)
                .in(KnowledgeRetrievalEvaluationLabel::getId, request.getIds()));
        clearLegacyTargetWhenNoLabels(caseId);
        return WebResponse.OK(I18nUtils.getMessage("knowledge.evaluation.label.batch-delete.success"));
    }

    /**
     * Labels supersede the legacy target columns on the case.  Once the last
     * label is removed, clear those columns as well so a deleted label cannot
     * reappear through legacy fallback during health checks or a later run.
     */
    private void clearLegacyTargetWhenNoLabels(String caseId) {
        if (labelMapper.selectCount(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationLabel.class)
                .eq(KnowledgeRetrievalEvaluationLabel::getEvaluationCaseId, caseId)
                .eq(KnowledgeRetrievalEvaluationLabel::getDeleted, false)) > 0)
            return;
        KnowledgeRetrievalEvaluationCaseEntity item = caseMapper.selectById(caseId);
        if (item == null || Boolean.TRUE.equals(item.getDeleted())) return;
        item.setDocumentId(null);
        item.setSectionPath(null);
        item.setChunkId(null);
        item.setTargetType(null);
        caseMapper.updateById(item);
    }

    /**
     * 发布Version。
     */
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write)
    @PostMapping("/sets/{id}/versions")
    public WebResponse<String> publishVersion(@PathVariable String id) {
        KnowledgeRetrievalEvaluationSet set = setMapper.selectById(id);
        if (set == null || Boolean.TRUE.equals(set.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("knowledge.evaluation.set.not-found"));
        requireHealthy(id);
        List<KnowledgeRetrievalEvaluationCaseEntity> cases = caseMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationCaseEntity.class).eq(KnowledgeRetrievalEvaluationCaseEntity::getEvaluationSetId, id).eq(KnowledgeRetrievalEvaluationCaseEntity::getDeleted, false).eq(KnowledgeRetrievalEvaluationCaseEntity::getStatus, 1));
        Set<String> caseIds = cases.stream().map(KnowledgeRetrievalEvaluationCaseEntity::getId).collect(Collectors.toSet());
        List<KnowledgeRetrievalEvaluationLabel> labels = caseIds.isEmpty() ? Collections.emptyList() : labelMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationLabel.class).in(KnowledgeRetrievalEvaluationLabel::getEvaluationCaseId, caseIds).eq(KnowledgeRetrievalEvaluationLabel::getDeleted, false).eq(KnowledgeRetrievalEvaluationLabel::getStatus, 1));
        KnowledgeRetrievalEvaluationSetVersion latest = setVersionMapper.selectOne(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationSetVersion.class).eq(KnowledgeRetrievalEvaluationSetVersion::getEvaluationSetId, id).eq(KnowledgeRetrievalEvaluationSetVersion::getDeleted, false).orderByDesc(KnowledgeRetrievalEvaluationSetVersion::getVersionNo).last("LIMIT 1"));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("set", set);
        snapshot.put("cases", cases);
        snapshot.put("labels", labels);
        KnowledgeRetrievalEvaluationSetVersion version = new KnowledgeRetrievalEvaluationSetVersion();
        version.setEvaluationSetId(id);
        version.setVersionNo(latest == null ? 1 : latest.getVersionNo() + 1);
        version.setSnapshotJson(JSON.toJSONString(snapshot));
        version.setPublishedAt(System.currentTimeMillis());
        setVersionMapper.insert(version);
        return WebResponse.OK(I18nUtils.getMessage("knowledge.evaluation.version.publish.success"), version.getId());
    }

    /**
     * 处理versions。
     */
    @GetMapping("/sets/{id}/versions")
    public WebResponse<List<KnowledgeRetrievalEvaluationSetVersion>> versions(@PathVariable String id) {
        return WebResponse.OK(setVersionMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationSetVersion.class).eq(KnowledgeRetrievalEvaluationSetVersion::getEvaluationSetId, id).eq(KnowledgeRetrievalEvaluationSetVersion::getDeleted, false).orderByDesc(KnowledgeRetrievalEvaluationSetVersion::getVersionNo)));
    }

    /**
     * 处理health。
     */
    @GetMapping("/sets/{id}/health")
    public WebResponse<KnowledgeRetrievalEvaluationHealthVo> health(@PathVariable String id) {
        return WebResponse.OK(evaluationSetHealth(id));
    }

    /**
     * Returns all state needed to decide the next evaluation-management action from one snapshot.
     */
    @GetMapping("/sets/{id}/workbench")
    public WebResponse<KnowledgeRetrievalEvaluationWorkbenchVo> workbench(@PathVariable String id) {
        KnowledgeRetrievalEvaluationWorkbenchVo response = new KnowledgeRetrievalEvaluationWorkbenchVo();
        response.setEvaluationSet(requireSet(id));
        response.setHealth(evaluationSetHealth(id));
        response.setVersions(setVersionMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationSetVersion.class)
                .eq(KnowledgeRetrievalEvaluationSetVersion::getEvaluationSetId, id)
                .eq(KnowledgeRetrievalEvaluationSetVersion::getDeleted, false)
                .orderByDesc(KnowledgeRetrievalEvaluationSetVersion::getVersionNo)));
        response.setRuns(runMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationRun.class)
                .eq(KnowledgeRetrievalEvaluationRun::getEvaluationSetId, id)
                .eq(KnowledgeRetrievalEvaluationRun::getDeleted, false)
                .orderByDesc(KnowledgeRetrievalEvaluationRun::getStartedAt)));
        response.setTrend(runMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationRun.class)
                .eq(KnowledgeRetrievalEvaluationRun::getEvaluationSetId, id)
                .eq(KnowledgeRetrievalEvaluationRun::getDeleted, false)
                .in(KnowledgeRetrievalEvaluationRun::getStatus, "SUCCEEDED", "PARTIAL_FAILED", "FAILED", "CANCELLED")
                .orderByAsc(KnowledgeRetrievalEvaluationRun::getStartedAt)));
        return WebResponse.OK(response);
    }

    /**
     * 查询未删除的评测集，并按创建时间倒序返回。
     */
    @GetMapping("/sets")
    public WebResponse<List<KnowledgeRetrievalEvaluationSet>> sets(@RequestParam(defaultValue = "1") Long current, @RequestParam(defaultValue = "10") Long pageSize, @RequestParam(required = false) String name, @RequestParam(required = false) String agentDefinitionId) {
        Page<KnowledgeRetrievalEvaluationSet> page = setMapper.selectPage(new Page<>(Math.max(current, 1), Math.min(Math.max(pageSize, 1), 100)), Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationSet.class)
                .eq(KnowledgeRetrievalEvaluationSet::getDeleted, false)
                .like(StringUtils.isNotBlank(name), KnowledgeRetrievalEvaluationSet::getName, name)
                .like(StringUtils.isNotBlank(agentDefinitionId), KnowledgeRetrievalEvaluationSet::getAgentDefinitionId, agentDefinitionId)
                .orderByDesc(KnowledgeRetrievalEvaluationSet::getCreatedAt));
        return WebResponse.Page(page.getRecords(), page.getTotal());
    }

    /**
     * 处理set。
     */
    @GetMapping("/sets/{id}")
    public WebResponse<KnowledgeRetrievalEvaluationSet> set(@PathVariable String id) {
        KnowledgeRetrievalEvaluationSet item = setMapper.selectById(id);
        if (item == null || Boolean.TRUE.equals(item.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("knowledge.evaluation.set.not-found"));
        return WebResponse.OK(item);
    }

    /**
     * 保存Set。
     */
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write)
    @PostMapping("/sets")
    public WebResponse<String> saveSet(@RequestBody KnowledgeRetrievalEvaluationSet set) {
        if (StringUtils.isBlank(set.getAgentDefinitionId()) || StringUtils.isBlank(set.getName()))
            throw new ServerException(400, I18nUtils.getMessage("knowledge.evaluation.set.agent-definition-and-name.required"));
        if (set.getStatus() == null) set.setStatus(1);
        setMapper.insert(set);
        return WebResponse.OK(I18nUtils.getMessage("knowledge.evaluation.set.create.success"), set.getId());
    }

    /**
     * 更新Set。
     */
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write)
    @PutMapping("/sets/{id}")
    public WebResponse<Void> updateSet(@PathVariable String id, @RequestBody KnowledgeRetrievalEvaluationSet set) {
        KnowledgeRetrievalEvaluationSet existing = requireSet(id);
        if (StringUtils.isBlank(set.getAgentDefinitionId()) || StringUtils.isBlank(set.getName()))
            throw new ServerException(400, I18nUtils.getMessage("knowledge.evaluation.set.agent-definition-and-name.required"));
        set.setId(existing.getId());
        set.setDeleted(existing.getDeleted());
        setMapper.updateById(set);
        return WebResponse.OK(I18nUtils.getMessage("knowledge.evaluation.set.update.success"));
    }

    /**
     * 删除Set。
     */
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write)
    @DeleteMapping("/sets/{id}")
    public WebResponse<Void> deleteSet(@PathVariable String id) {
        KnowledgeRetrievalEvaluationSet set = requireSet(id);
        if (runMapper.selectCount(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationRun.class).eq(KnowledgeRetrievalEvaluationRun::getEvaluationSetId, id).eq(KnowledgeRetrievalEvaluationRun::getDeleted, false)) > 0)
            throw new ServerException(409, I18nUtils.getMessage("knowledge.evaluation.set.delete.run-history.blocked"));
        set.setDeleted(true);
        setMapper.updateById(set);
        return WebResponse.OK(I18nUtils.getMessage("knowledge.evaluation.set.delete.success"));
    }

    /**
     * 查询指定评测集下未删除的评测问题。
     */
    @GetMapping("/sets/{id}/cases")
    public WebResponse<List<KnowledgeRetrievalEvaluationCaseEntity>> cases(@PathVariable String id) {
        return WebResponse.OK(caseMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationCaseEntity.class).eq(KnowledgeRetrievalEvaluationCaseEntity::getEvaluationSetId, id).eq(KnowledgeRetrievalEvaluationCaseEntity::getDeleted, false)));
    }

    /**
     * 保存Case。
     */
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write)
    @PostMapping("/sets/{id}/cases")
    public WebResponse<String> saveCase(@PathVariable String id, @RequestBody KnowledgeRetrievalEvaluationCaseEntity item) {
        if (StringUtils.isBlank(item.getQuestion()))
            throw new ServerException(400, I18nUtils.getMessage("knowledge.evaluation.case.question.required"));
        item.setEvaluationSetId(id);
        if (item.getStatus() == null) item.setStatus(1);
        caseMapper.insert(item);
        return WebResponse.OK(I18nUtils.getMessage("knowledge.evaluation.case.create.success"), item.getId());
    }

    /**
     * 更新Case。
     */
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write)
    @PutMapping("/sets/{setId}/cases/{caseId}")
    public WebResponse<Void> updateCase(@PathVariable String setId, @PathVariable String caseId, @RequestBody KnowledgeRetrievalEvaluationCaseEntity item) {
        KnowledgeRetrievalEvaluationCaseEntity existing = requireCase(setId, caseId);
        if (StringUtils.isBlank(item.getQuestion()))
            throw new ServerException(400, I18nUtils.getMessage("knowledge.evaluation.case.question.required"));
        item.setId(existing.getId());
        item.setEvaluationSetId(existing.getEvaluationSetId());
        item.setDeleted(existing.getDeleted());
        caseMapper.updateById(item);
        return WebResponse.OK(I18nUtils.getMessage("knowledge.evaluation.case.update.success"));
    }

    /**
     * 删除Case。
     */
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write)
    @DeleteMapping("/sets/{setId}/cases/{caseId}")
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<Void> deleteCase(@PathVariable String setId, @PathVariable String caseId) {
        KnowledgeRetrievalEvaluationCaseEntity item = requireCase(setId, caseId);
        // Use MyBatis-Plus logical deletion consistently and remove dependent labels in the same transaction.
        caseMapper.deleteById(item.getId());
        labelMapper.delete(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationLabel.class)
                .eq(KnowledgeRetrievalEvaluationLabel::getEvaluationCaseId, item.getId()));
        return WebResponse.OK(I18nUtils.getMessage("knowledge.evaluation.case.delete.success"));
    }

    /**
     * 处理batch删除Cases。
     */
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write)
    @PostMapping("/sets/{id}/cases/batch-delete")
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<Void> batchDeleteCases(@PathVariable String id, @RequestBody IdsRequest request) {
        requireSet(id);
        requireIds(request);
        List<KnowledgeRetrievalEvaluationCaseEntity> items = caseMapper.selectList(
                Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationCaseEntity.class)
                        .eq(KnowledgeRetrievalEvaluationCaseEntity::getEvaluationSetId, id)
                        .in(KnowledgeRetrievalEvaluationCaseEntity::getId, request.getIds())
                        .eq(KnowledgeRetrievalEvaluationCaseEntity::getDeleted, false));
        if (!items.isEmpty()) {
            List<String> caseIds = items.stream().map(KnowledgeRetrievalEvaluationCaseEntity::getId)
                    .collect(Collectors.toList());
            caseMapper.deleteBatchIds(caseIds);
            labelMapper.delete(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationLabel.class)
                    .in(KnowledgeRetrievalEvaluationLabel::getEvaluationCaseId, caseIds));
        }
        return WebResponse.OK(I18nUtils.getMessage("knowledge.evaluation.case.batch-delete.success"));
    }

    /**
     * 处理batchCase状态。
     */
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write)
    @PostMapping("/sets/{id}/cases/batch-status")
    public WebResponse<Void> batchCaseStatus(@PathVariable String id, @RequestBody CaseStatusRequest request) {
        if (request == null || request.getCaseIds() == null || request.getCaseIds().isEmpty() || request.getStatus() == null || request.getStatus() != 0 && request.getStatus() != 1)
            throw new ServerException(400, I18nUtils.getMessage("knowledge.evaluation.case.batch-status.required"));
        caseMapper.update(null, Wrappers.lambdaUpdate(KnowledgeRetrievalEvaluationCaseEntity.class).eq(KnowledgeRetrievalEvaluationCaseEntity::getEvaluationSetId, id).in(KnowledgeRetrievalEvaluationCaseEntity::getId, request.getCaseIds()).eq(KnowledgeRetrievalEvaluationCaseEntity::getDeleted, false).set(KnowledgeRetrievalEvaluationCaseEntity::getStatus, request.getStatus()));
        return WebResponse.OK(I18nUtils.getMessage("knowledge.evaluation.case.status.update.success"));
    }

    /**
     * 处理exportCases。
     */
    @GetMapping("/sets/{id}/cases/export")
    public WebResponse<List<KnowledgeRetrievalEvaluationCaseTransferVo>> exportCases(@PathVariable String id) {
        List<KnowledgeRetrievalEvaluationCaseEntity> cases = caseMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationCaseEntity.class).eq(KnowledgeRetrievalEvaluationCaseEntity::getEvaluationSetId, id).eq(KnowledgeRetrievalEvaluationCaseEntity::getDeleted, false));
        Set<String> caseIds = cases.stream().map(KnowledgeRetrievalEvaluationCaseEntity::getId).collect(Collectors.toSet());
        List<KnowledgeRetrievalEvaluationLabel> labels = caseIds.isEmpty() ? Collections.emptyList() : labelMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationLabel.class).in(KnowledgeRetrievalEvaluationLabel::getEvaluationCaseId, caseIds).eq(KnowledgeRetrievalEvaluationLabel::getDeleted, false));
        return WebResponse.OK(cases.stream().map(item -> {
            KnowledgeRetrievalEvaluationCaseTransferVo result = new KnowledgeRetrievalEvaluationCaseTransferVo();
            result.setItem(item);
            result.setLabels(labels.stream().filter(label -> item.getId().equals(label.getEvaluationCaseId())).collect(Collectors.toList()));
            return result;
        }).collect(Collectors.toList()));
    }

    /**
     * 预览Import。
     */
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write)
    @PostMapping("/sets/{id}/cases/import/preview")
    public WebResponse<KnowledgeRetrievalEvaluationImportPreviewVo> previewImport(@PathVariable String id, @RequestBody List<KnowledgeRetrievalEvaluationCaseTransferVo> items) {
        return WebResponse.OK(validateImport(requireSet(id), items));
    }

    /**
     * 处理importCases。
     */
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write)
    @PostMapping("/sets/{id}/cases/import")
    public WebResponse<Integer> importCases(@PathVariable String id, @RequestBody List<KnowledgeRetrievalEvaluationCaseTransferVo> items) {
        KnowledgeRetrievalEvaluationImportPreviewVo preview = validateImport(requireSet(id), items);
        if (!preview.isValid())
            throw new ServerException(400, I18nUtils.getMessage("knowledge.evaluation.case.import.invalid"));
        for (KnowledgeRetrievalEvaluationCaseTransferVo transfer : items) {
            KnowledgeRetrievalEvaluationCaseEntity item = transfer.getItem();
            item.setId(null);
            item.setEvaluationSetId(id);
            if (item.getStatus() == null) item.setStatus(1);
            caseMapper.insert(item);
            for (KnowledgeRetrievalEvaluationLabel label : transfer.getLabels() == null ? Collections.<KnowledgeRetrievalEvaluationLabel>emptyList() : transfer.getLabels()) {
                label.setId(null);
                label.setEvaluationCaseId(item.getId());
                if (label.getStatus() == null) label.setStatus(1);
                if (label.getRelevanceGrade() == null) label.setRelevanceGrade(1);
                if (label.getIsRequired() == null) label.setIsRequired(true);
                labelMapper.insert(label);
            }
        }
        return WebResponse.OK(I18nUtils.getMessage("knowledge.evaluation.case.import.success"), items.size());
    }

    /**
     * 执行评测集：冻结标注和结果，区分无效标注与检索异常。
     */
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write)
    @PostMapping("/sets/{id}/run")
    public WebResponse<KnowledgeRetrievalEvaluationReport> runSet(@PathVariable String id) {
        KnowledgeRetrievalEvaluationSet set = setMapper.selectById(id);
        if (set == null || Boolean.TRUE.equals(set.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("knowledge.evaluation.set.not-found"));
        List<KnowledgeRetrievalEvaluationCaseEntity> cases = caseMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationCaseEntity.class).eq(KnowledgeRetrievalEvaluationCaseEntity::getEvaluationSetId, id).eq(KnowledgeRetrievalEvaluationCaseEntity::getDeleted, false).eq(KnowledgeRetrievalEvaluationCaseEntity::getStatus, 1));
        List<KnowledgeRetrievalEvaluationCase> inputs = new ArrayList<>();
        List<KnowledgeRetrievalEvaluationCaseEntity> validCases = new ArrayList<>();
        List<KnowledgeRetrievalEvaluationCaseEntity> invalidCases = new ArrayList<>();
        for (KnowledgeRetrievalEvaluationCaseEntity item : cases) {
            String targetType = StringUtils.upperCase(StringUtils.defaultIfBlank(item.getTargetType(), StringUtils.isNotBlank(item.getSectionPath()) ? "SECTION" : "DOCUMENT"));
            if (StringUtils.isBlank(item.getQuestion()) || !"DOCUMENT".equals(targetType) && !"SECTION".equals(targetType) && !"CHUNK".equals(targetType) || "SECTION".equals(targetType) && StringUtils.isBlank(item.getSectionPath())) {
                invalidCases.add(item);
                continue;
            }
            List<KnowledgeDocumentChunk> chunks;
            if ("CHUNK".equals(targetType))
                chunks = StringUtils.isBlank(item.getChunkId()) ? Collections.<KnowledgeDocumentChunk>emptyList() : chunkService.list(Wrappers.lambdaQuery(KnowledgeDocumentChunk.class).eq(KnowledgeDocumentChunk::getId, item.getChunkId()).eq(KnowledgeDocumentChunk::getDeleted, false).eq(KnowledgeDocumentChunk::getDocumentId, item.getDocumentId()));
            else
                chunks = StringUtils.isBlank(item.getDocumentId()) ? Collections.<KnowledgeDocumentChunk>emptyList() : chunkService.list(Wrappers.lambdaQuery(KnowledgeDocumentChunk.class).eq(KnowledgeDocumentChunk::getDocumentId, item.getDocumentId()).eq(KnowledgeDocumentChunk::getDeleted, false).eq("SECTION".equals(targetType), KnowledgeDocumentChunk::getSectionPath, item.getSectionPath()));
            if (chunks.isEmpty()) {
                invalidCases.add(item);
                continue;
            }
            KnowledgeRetrievalEvaluationCase input = new KnowledgeRetrievalEvaluationCase();
            input.setQuestion(item.getQuestion());
            input.setTargetType(targetType);
            input.setExpectedChunkIds(chunks.stream().map(KnowledgeDocumentChunk::getId).collect(Collectors.toList()));
            inputs.add(input);
            validCases.add(item);
        }
        long startedAt = System.currentTimeMillis();
        KnowledgeRetrievalEvaluationReport report = evaluationService.evaluate(set.getAgentDefinitionId(), inputs);
        KnowledgeRetrievalEvaluationRun run = new KnowledgeRetrievalEvaluationRun();
        run.setEvaluationSetId(id);
        run.setAgentDefinitionIdSnapshot(set.getAgentDefinitionId());
        run.setRetrievalConfigSnapshot(retrievalConfigSnapshot(set.getAgentDefinitionId(), cases, Collections.emptyList()));
        run.setStatus(report.getFailedCount() == 0 ? "SUCCEEDED" : report.getTotal() == 0 ? "FAILED" : "PARTIAL_FAILED");
        run.setDatasetSnapshot(JSON.toJSONString(cases));
        run.setRunConfigSnapshot(JSON.toJSONString(Collections.singletonMap("executionMode", "SYNCHRONOUS")));
        run.setTotalCount(report.getTotal());
        run.setInvalidCount(invalidCases.size());
        run.setFailedCount(report.getFailedCount());
        run.setRecallAtK(report.getRecallAtK());
        run.setMrr(report.getMrr());
        run.setNdcg(report.getNdcg());
        run.setStartedAt(startedAt);
        run.setFinishedAt(System.currentTimeMillis());
        run.setErrorSummaryJson(report.getFailedCount() == 0 ? null : JSON.toJSONString(Collections.singletonMap("RETRIEVAL_FAILED", report.getFailedCount())));
        runMapper.insert(run);
        for (int i = 0; i < report.getItems().size(); i++) {
            KnowledgeRetrievalEvaluationReport.Item item = report.getItems().get(i);
            KnowledgeRetrievalEvaluationCaseEntity source = validCases.get(i);
            KnowledgeRetrievalEvaluationResult result = new KnowledgeRetrievalEvaluationResult();
            result.setRunId(run.getId());
            result.setEvaluationCaseId(source.getId());
            result.setStatus(item.getStatus());
            result.setQuestionSnapshot(source.getQuestion());
            result.setExpectedDocumentIdSnapshot(source.getDocumentId());
            result.setExpectedDocumentTitleSnapshot(documentTitle(source.getDocumentId()));
            result.setExpectedSectionPathSnapshot(source.getSectionPath());
            result.setTargetTypeSnapshot(inputs.get(i).getTargetType());
            result.setExpectedChunkIdsSnapshot(JSON.toJSONString(inputs.get(i).getExpectedChunkIds()));
            result.setRetrievedChunkIds(JSON.toJSONString(item.getRetrievedChunkIds() == null ? Collections.emptyList() : item.getRetrievedChunkIds()));
            result.setRecallAtK(item.getRecallAtK());
            result.setMrr(item.getMrr());
            result.setNdcg(item.getNdcg());
            result.setErrorCode(item.getErrorCode());
            result.setErrorMessage(item.getErrorMessage());
            resultMapper.insert(result);
        }
        for (KnowledgeRetrievalEvaluationCaseEntity source : invalidCases) {
            KnowledgeRetrievalEvaluationResult result = new KnowledgeRetrievalEvaluationResult();
            result.setRunId(run.getId());
            result.setEvaluationCaseId(source.getId());
            result.setStatus("INVALID_LABEL");
            result.setQuestionSnapshot(source.getQuestion());
            result.setExpectedDocumentIdSnapshot(source.getDocumentId());
            result.setExpectedDocumentTitleSnapshot(documentTitle(source.getDocumentId()));
            result.setExpectedSectionPathSnapshot(source.getSectionPath());
            result.setTargetTypeSnapshot(source.getTargetType());
            result.setRetrievedChunkIds("[]");
            result.setRetrievedItemsSnapshot("[]");
            result.setErrorCode("TARGET_NOT_RESOLVED");
            result.setErrorMessage(I18nUtils.getMessage("knowledge.evaluation.error.target-not-resolved"));
            resultMapper.insert(result);
        }
        return WebResponse.OK(report);
    }

    /**
     * 查询评测集历史运行记录，用于趋势图和运行对比。
     */
    @GetMapping("/sets/{id}/runs")
    public WebResponse<List<KnowledgeRetrievalEvaluationRun>> runs(@PathVariable String id) {
        return WebResponse.OK(runMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationRun.class).eq(KnowledgeRetrievalEvaluationRun::getEvaluationSetId, id).eq(KnowledgeRetrievalEvaluationRun::getDeleted, false).orderByDesc(KnowledgeRetrievalEvaluationRun::getStartedAt)));
    }

    /**
     * Marks a completed run as the one baseline for this evaluation set.
     */
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write)
    @PostMapping("/sets/{setId}/runs/{runId}/baseline")
    public WebResponse<Void> setBaseline(@PathVariable String setId, @PathVariable String runId) {
        KnowledgeRetrievalEvaluationRun run = requireRun(setId, runId);
        if (!"SUCCEEDED".equals(run.getStatus()) && !"PARTIAL_FAILED".equals(run.getStatus()))
            throw new ServerException(409, I18nUtils.getMessage("knowledge.evaluation.run.baseline.status.invalid"));
        runMapper.update(null, Wrappers.lambdaUpdate(KnowledgeRetrievalEvaluationRun.class).eq(KnowledgeRetrievalEvaluationRun::getEvaluationSetId, setId).eq(KnowledgeRetrievalEvaluationRun::getIsBaseline, true).set(KnowledgeRetrievalEvaluationRun::getIsBaseline, false));
        run.setIsBaseline(true);
        runMapper.updateById(run);
        return WebResponse.OK(I18nUtils.getMessage("knowledge.evaluation.run.baseline.success"));
    }

    /**
     * Returns the run history for trend charts without recomputing persisted metrics.
     */
    @GetMapping("/sets/{setId}/trend")
    public WebResponse<List<KnowledgeRetrievalEvaluationRun>> trend(@PathVariable String setId) {
        return WebResponse.OK(runMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationRun.class).eq(KnowledgeRetrievalEvaluationRun::getEvaluationSetId, setId).eq(KnowledgeRetrievalEvaluationRun::getDeleted, false).in(KnowledgeRetrievalEvaluationRun::getStatus, "SUCCEEDED", "PARTIAL_FAILED", "FAILED", "CANCELLED").orderByAsc(KnowledgeRetrievalEvaluationRun::getStartedAt)));
    }

    /**
     * Compares aggregate metrics and every common evaluation case from two completed runs.
     */
    @GetMapping("/sets/{setId}/runs/compare")
    public WebResponse<KnowledgeRetrievalEvaluationComparisonVo> compareRuns(@PathVariable String setId, @RequestParam String baselineRunId, @RequestParam String candidateRunId) {
        KnowledgeRetrievalEvaluationRun baseline = requireRun(setId, baselineRunId), candidate = requireRun(setId, candidateRunId);
        if (baselineRunId.equals(candidateRunId))
            throw new ServerException(400, I18nUtils.getMessage("knowledge.evaluation.run.compare.same"));
        if (!Boolean.TRUE.equals(baseline.getIsBaseline()))
            throw new ServerException(400, I18nUtils.getMessage("knowledge.evaluation.run.compare.baseline.required"));
        if (!isCompletedRun(baseline) || !isCompletedRun(candidate))
            throw new ServerException(409, I18nUtils.getMessage("knowledge.evaluation.run.compare.status.invalid"));
        KnowledgeRetrievalEvaluationComparisonVo response = new KnowledgeRetrievalEvaluationComparisonVo();
        response.setBaselineRunId(baselineRunId);
        response.setCandidateRunId(candidateRunId);
        boolean sameVersion = StringUtils.isNotBlank(baseline.getEvaluationSetVersionId()) && baseline.getEvaluationSetVersionId().equals(candidate.getEvaluationSetVersionId());
        boolean sameSnapshot = StringUtils.isBlank(baseline.getEvaluationSetVersionId()) && StringUtils.isBlank(candidate.getEvaluationSetVersionId()) && StringUtils.equals(baseline.getDatasetSnapshot(), candidate.getDatasetSnapshot());
        response.setComparable(sameVersion || sameSnapshot);
        if (!response.isComparable())
            response.setNonComparableReason(I18nUtils.getMessage("knowledge.evaluation.run.compare.snapshot.mismatch"));
        KnowledgeRetrievalEvaluationComparisonVo.MetricDelta metrics = response.getMetrics();
        metrics.setBaselineRecallAtK(baseline.getRecallAtK());
        metrics.setCandidateRecallAtK(candidate.getRecallAtK());
        metrics.setRecallAtKDelta(delta(candidate.getRecallAtK(), baseline.getRecallAtK()));
        metrics.setBaselineMrr(baseline.getMrr());
        metrics.setCandidateMrr(candidate.getMrr());
        metrics.setMrrDelta(delta(candidate.getMrr(), baseline.getMrr()));
        metrics.setBaselineNdcg(baseline.getNdcg());
        metrics.setCandidateNdcg(candidate.getNdcg());
        metrics.setNdcgDelta(delta(candidate.getNdcg(), baseline.getNdcg()));
        Map<String, KnowledgeRetrievalEvaluationResult> baselineResults = resultMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationResult.class).eq(KnowledgeRetrievalEvaluationResult::getRunId, baselineRunId).eq(KnowledgeRetrievalEvaluationResult::getDeleted, false)).stream().collect(Collectors.toMap(KnowledgeRetrievalEvaluationResult::getEvaluationCaseId, item -> item, (left, right) -> left));
        List<KnowledgeRetrievalEvaluationResult> candidateResults = resultMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationResult.class).eq(KnowledgeRetrievalEvaluationResult::getRunId, candidateRunId).eq(KnowledgeRetrievalEvaluationResult::getDeleted, false));
        for (KnowledgeRetrievalEvaluationResult candidateResult : candidateResults) {
            KnowledgeRetrievalEvaluationResult baselineResult = baselineResults.get(candidateResult.getEvaluationCaseId());
            if (baselineResult == null) continue;
            KnowledgeRetrievalEvaluationComparisonVo.CaseDelta item = new KnowledgeRetrievalEvaluationComparisonVo.CaseDelta();
            item.setEvaluationCaseId(candidateResult.getEvaluationCaseId());
            item.setQuestion(StringUtils.defaultIfBlank(candidateResult.getQuestionSnapshot(), baselineResult.getQuestionSnapshot()));
            item.setBaselineStatus(baselineResult.getStatus());
            item.setCandidateStatus(candidateResult.getStatus());
            item.setBaselineRecallAtK(baselineResult.getRecallAtK());
            item.setCandidateRecallAtK(candidateResult.getRecallAtK());
            item.setRecallAtKDelta(delta(candidateResult.getRecallAtK(), baselineResult.getRecallAtK()));
            item.setBaselineMrr(baselineResult.getMrr());
            item.setCandidateMrr(candidateResult.getMrr());
            item.setMrrDelta(delta(candidateResult.getMrr(), baselineResult.getMrr()));
            item.setBaselineNdcg(baselineResult.getNdcg());
            item.setCandidateNdcg(candidateResult.getNdcg());
            item.setNdcgDelta(delta(candidateResult.getNdcg(), baselineResult.getNdcg()));
            response.getCases().add(item);
        }
        return WebResponse.OK(response);
    }

    /**
     * 评测标注使用的公共文档选项接口，数据仍按当前用户知识库可见范围过滤。
     */
    @ApiOperation("评测集可标注文档")
    @Permission(required = false)
    @GetMapping("/documents")
    public WebResponse<List<KnowledgeDocument>> documents(@RequestParam(value = "keyword", required = false) String keyword) {
        List<String> readableBaseIds = knowledgeAccessService.readableKnowledgeBaseIds();
        if (readableBaseIds.isEmpty()) return WebResponse.OK(Collections.emptyList());
        return WebResponse.OK(documentService.list(Wrappers.lambdaQuery(KnowledgeDocument.class)
                .in(KnowledgeDocument::getKnowledgeBaseId, readableBaseIds)
                .like(StringUtils.isNotBlank(keyword), KnowledgeDocument::getTitle, keyword)
                .eq(KnowledgeDocument::getDeleted, false)
                .orderByDesc(KnowledgeDocument::getCreatedAt)));
    }

    /**
     * 查询指定文档当前已有分块中的去重章节路径。
     */
    @ApiOperation("文档可标注章节")
    @Permission(required = false)
    @GetMapping("/documents/{id}/sections")
    public WebResponse<List<String>> sections(@PathVariable String id) {
        KnowledgeDocument document = documentService.getById(id);
        if (document == null || Boolean.TRUE.equals(document.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("knowledge.document.not-found"));
        knowledgeAccessService.requireReadable(document.getKnowledgeBaseId());
        List<String> sections = chunkService.list(Wrappers.lambdaQuery(KnowledgeDocumentChunk.class)
                        .eq(KnowledgeDocumentChunk::getDocumentId, id)
                        .eq(KnowledgeDocumentChunk::getDeleted, false)
                        .isNotNull(KnowledgeDocumentChunk::getSectionPath)
                        .ne(KnowledgeDocumentChunk::getSectionPath, ""))
                .stream().map(KnowledgeDocumentChunk::getSectionPath).distinct().sorted().collect(Collectors.toList());
        return WebResponse.OK(sections);
    }

    /**
     * 文档可标注分块。
     */
    @ApiOperation("文档可标注分块")
    @Permission(required = false)
    @GetMapping("/documents/{id}/chunks")
    public WebResponse<List<Map<String, Object>>> chunks(@PathVariable String id) {
        KnowledgeDocument document = documentService.getById(id);
        if (document == null || Boolean.TRUE.equals(document.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("knowledge.document.not-found"));
        knowledgeAccessService.requireReadable(document.getKnowledgeBaseId());
        return WebResponse.OK(chunkService.list(Wrappers.lambdaQuery(KnowledgeDocumentChunk.class).eq(KnowledgeDocumentChunk::getDocumentId, id).eq(KnowledgeDocumentChunk::getDeleted, false).orderByAsc(KnowledgeDocumentChunk::getChunkIndex)).stream().map(chunk -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", chunk.getId());
            item.put("chunkIndex", chunk.getChunkIndex());
            item.put("sectionPath", chunk.getSectionPath());
            return item;
        }).collect(Collectors.toList()));
    }

    /**
     * 将持久化结果补充问题、文档和召回分块信息，供前端展开查看。
     */
    @ApiOperation("单次运行逐题结果")
    @GetMapping("/sets/{setId}/runs/{runId}/results")
    public WebResponse<List<KnowledgeRetrievalEvaluationResultVo>> results(@PathVariable String setId,
                                                                           @PathVariable String runId,
                                                                           @RequestParam(defaultValue = "1") Long current,
                                                                           @RequestParam(defaultValue = "10") Long pageSize,
                                                                           @RequestParam(required = false) String status,
                                                                           @RequestParam(required = false, name = "question") String questionKeyword) {
        KnowledgeRetrievalEvaluationRun run = runMapper.selectById(runId);
        if (run == null || Boolean.TRUE.equals(run.getDeleted()) || !setId.equals(run.getEvaluationSetId()))
            throw new ServerException(404, I18nUtils.getMessage("knowledge.evaluation.run.not-found"));
        long safeCurrent = Math.max(1L, current);
        long safePageSize = Math.min(100L, Math.max(1L, pageSize));
        Page<KnowledgeRetrievalEvaluationResult> page = resultMapper.selectPage(new Page<>(safeCurrent, safePageSize),
                Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationResult.class)
                        .eq(KnowledgeRetrievalEvaluationResult::getRunId, runId)
                        .eq(KnowledgeRetrievalEvaluationResult::getDeleted, false)
                        .eq(StringUtils.isNotBlank(status), KnowledgeRetrievalEvaluationResult::getStatus, status)
                        .like(StringUtils.isNotBlank(questionKeyword), KnowledgeRetrievalEvaluationResult::getQuestionSnapshot, questionKeyword)
                        .orderByAsc(KnowledgeRetrievalEvaluationResult::getCreatedAt));
        List<KnowledgeRetrievalEvaluationResult> results = page.getRecords();
        if (results.isEmpty()) return WebResponse.Page(Collections.emptyList(), page.getTotal());
        Set<String> legacyCaseIds = results.stream().filter(item -> StringUtils.isBlank(item.getQuestionSnapshot())).map(KnowledgeRetrievalEvaluationResult::getEvaluationCaseId).collect(Collectors.toSet());
        Map<String, KnowledgeRetrievalEvaluationCaseEntity> legacyCases = legacyCaseIds.isEmpty() ? Collections.emptyMap() : caseMapper.selectBatchIds(legacyCaseIds).stream()
                .collect(Collectors.toMap(KnowledgeRetrievalEvaluationCaseEntity::getId, item -> item));
        Set<String> chunkIds = new HashSet<>();
        for (KnowledgeRetrievalEvaluationResult result : results)
            if (StringUtils.isBlank(result.getRetrievedItemsSnapshot()))
                chunkIds.addAll(JSON.parseArray(StringUtils.defaultIfBlank(result.getRetrievedChunkIds(), "[]"), String.class));
        Map<String, KnowledgeDocumentChunk> chunks = chunkIds.isEmpty() ? Collections.emptyMap() : chunkService.listByIds(chunkIds).stream()
                .collect(Collectors.toMap(KnowledgeDocumentChunk::getId, item -> item));
        Set<String> documentIds = new HashSet<>();
        for (KnowledgeRetrievalEvaluationResult result : results) {
            if (StringUtils.isNotBlank(result.getExpectedDocumentIdSnapshot()) && StringUtils.isBlank(result.getExpectedDocumentTitleSnapshot()))
                documentIds.add(result.getExpectedDocumentIdSnapshot());
            else {
                KnowledgeRetrievalEvaluationCaseEntity legacyCase = legacyCases.get(result.getEvaluationCaseId());
                if (legacyCase != null && StringUtils.isNotBlank(legacyCase.getDocumentId()))
                    documentIds.add(legacyCase.getDocumentId());
            }
        }
        for (KnowledgeDocumentChunk chunk : chunks.values())
            if (StringUtils.isNotBlank(chunk.getDocumentId())) documentIds.add(chunk.getDocumentId());
        Map<String, String> titles = documentIds.isEmpty() ? Collections.emptyMap() : documentService.listByIds(documentIds).stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, KnowledgeDocument::getTitle));
        List<KnowledgeRetrievalEvaluationResultVo> response = new ArrayList<>();
        for (KnowledgeRetrievalEvaluationResult result : results) {
            KnowledgeRetrievalEvaluationCaseEntity legacyCase = legacyCases.get(result.getEvaluationCaseId());
            String question = StringUtils.defaultIfBlank(result.getQuestionSnapshot(), legacyCase == null ? null : legacyCase.getQuestion());
            String documentId = StringUtils.defaultIfBlank(result.getExpectedDocumentIdSnapshot(), legacyCase == null ? null : legacyCase.getDocumentId());
            String sectionPath = StringUtils.defaultIfBlank(result.getExpectedSectionPathSnapshot(), legacyCase == null ? null : legacyCase.getSectionPath());
            KnowledgeRetrievalEvaluationResultVo item = new KnowledgeRetrievalEvaluationResultVo();
            item.setId(result.getId());
            item.setEvaluationCaseId(result.getEvaluationCaseId());
            item.setQuestion(question);
            item.setExpectedDocumentId(documentId);
            item.setExpectedDocumentTitle(StringUtils.defaultIfBlank(result.getExpectedDocumentTitleSnapshot(), titles.get(documentId)));
            item.setExpectedSectionPath(sectionPath);
            item.setTargetType(result.getTargetTypeSnapshot());
            item.setExpectedChunkIds(JSON.parseArray(StringUtils.defaultIfBlank(result.getExpectedChunkIdsSnapshot(), "[]"), String.class));
            item.setStatus(result.getStatus());
            item.setRecallAtK(result.getRecallAtK());
            item.setMrr(result.getMrr());
            item.setNdcg(result.getNdcg());
            item.setErrorCode(result.getErrorCode());
            item.setErrorMessage(result.getErrorMessage());
            List<KnowledgeRetrievalEvaluationResultVo.RetrievedChunk> retrieved = new ArrayList<>();
            List<Map> retrievedSnapshots = JSON.parseArray(StringUtils.defaultIfBlank(result.getRetrievedItemsSnapshot(), "[]"), Map.class);
            if (!retrievedSnapshots.isEmpty()) for (Map snapshot : retrievedSnapshots) {
                KnowledgeRetrievalEvaluationResultVo.RetrievedChunk source = new KnowledgeRetrievalEvaluationResultVo.RetrievedChunk();
                source.setId((String) snapshot.get("id"));
                source.setDocumentId((String) snapshot.get("documentId"));
                source.setDocumentTitle((String) snapshot.get("documentTitle"));
                source.setSectionPath((String) snapshot.get("sectionPath"));
                source.setChunkIndex(snapshot.get("chunkIndex") == null ? null : ((Number) snapshot.get("chunkIndex")).intValue());
                source.setRank(snapshot.get("rank") == null ? null : ((Number) snapshot.get("rank")).intValue());
                retrieved.add(source);
            }
            else
                for (int index = 0; index < JSON.parseArray(StringUtils.defaultIfBlank(result.getRetrievedChunkIds(), "[]"), String.class).size(); index++) {
                    String chunkId = JSON.parseArray(StringUtils.defaultIfBlank(result.getRetrievedChunkIds(), "[]"), String.class).get(index);
                    KnowledgeDocumentChunk chunk = chunks.get(chunkId);
                    if (chunk == null) continue;
                    KnowledgeRetrievalEvaluationResultVo.RetrievedChunk source = new KnowledgeRetrievalEvaluationResultVo.RetrievedChunk();
                    source.setId(chunk.getId());
                    source.setDocumentId(chunk.getDocumentId());
                    source.setDocumentTitle(titles.get(chunk.getDocumentId()));
                    source.setSectionPath(chunk.getSectionPath());
                    source.setChunkIndex(chunk.getChunkIndex());
                    source.setRank(index + 1);
                    retrieved.add(source);
                }
            item.setRetrievedChunks(retrieved);
            response.add(item);
        }
        return WebResponse.Page(response, page.getTotal());
    }

    /**
     * 执行当前任务。
     */
    @ApiOperation("批量评测知识库检索命中率")
    @Permission(path = "/knowledge/base", type = Permission.Type.Write)
    @PostMapping("/run")
    public WebResponse<KnowledgeRetrievalEvaluationReport> run(@RequestBody Request request) {
        if (request == null || StringUtils.isBlank(request.getAgentDefinitionId())) {
            throw new ServerException(400, I18nUtils.getMessage("knowledge.evaluation.agent-definition.required"));
        }
        return WebResponse.OK(evaluationService.evaluate(request.getAgentDefinitionId(), request.getCases()));
    }

    /**
     * 表示Request。
     */
    public static class Request {
        private String agentDefinitionId;
        private List<KnowledgeRetrievalEvaluationCase> cases;

        /**
         * 获取智能体DefinitionId。
         */
        public String getAgentDefinitionId() {
            return agentDefinitionId;
        }

        /**
         * 处理set智能体DefinitionId。
         */
        public void setAgentDefinitionId(String agentDefinitionId) {
            this.agentDefinitionId = agentDefinitionId;
        }

        /**
         * 获取Cases。
         */
        public List<KnowledgeRetrievalEvaluationCase> getCases() {
            return cases;
        }

        /**
         * 处理setCases。
         */
        public void setCases(List<KnowledgeRetrievalEvaluationCase> cases) {
            this.cases = cases;
        }
    }

    /**
     * 表示Case状态Request。
     */
    public static class CaseStatusRequest {
        private List<String> caseIds;
        private Integer status;

        /**
         * 获取CaseIds。
         */
        public List<String> getCaseIds() {
            return caseIds;
        }

        /**
         * 处理setCaseIds。
         */
        public void setCaseIds(List<String> caseIds) {
            this.caseIds = caseIds;
        }

        /**
         * 获取状态。
         */
        public Integer getStatus() {
            return status;
        }

        /**
         * 处理set状态。
         */
        public void setStatus(Integer status) {
            this.status = status;
        }
    }

    /**
     * 表示IdsRequest。
     */
    public static class IdsRequest {
        private List<String> ids;

        /**
         * 获取Ids。
         */
        public List<String> getIds() {
            return ids;
        }

        /**
         * 处理setIds。
         */
        public void setIds(List<String> ids) {
            this.ids = ids;
        }
    }

    /**
     * 处理requireIds。
     */
    private void requireIds(IdsRequest request) {
        if (request == null || request.getIds() == null || request.getIds().isEmpty()
                || request.getIds().stream().anyMatch(StringUtils::isBlank))
            throw new ServerException(400, I18nUtils.getMessage("knowledge.evaluation.batch-delete.ids.required"));
    }

    /**
     * 处理requireCase。
     */
    private KnowledgeRetrievalEvaluationCaseEntity requireCase(String setId, String caseId) {
        KnowledgeRetrievalEvaluationCaseEntity item = caseMapper.selectById(caseId);
        if (item == null || Boolean.TRUE.equals(item.getDeleted()) || !setId.equals(item.getEvaluationSetId()))
            throw new ServerException(404, I18nUtils.getMessage("knowledge.evaluation.case.not-found"));
        return item;
    }

    /**
     * 处理requireSet。
     */
    private KnowledgeRetrievalEvaluationSet requireSet(String setId) {
        KnowledgeRetrievalEvaluationSet set = setMapper.selectById(setId);
        if (set == null || Boolean.TRUE.equals(set.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("knowledge.evaluation.set.not-found"));
        return set;
    }

    /**
     * 校验Import。
     */
    private KnowledgeRetrievalEvaluationImportPreviewVo validateImport(KnowledgeRetrievalEvaluationSet set, List<KnowledgeRetrievalEvaluationCaseTransferVo> items) {
        KnowledgeRetrievalEvaluationImportPreviewVo response = new KnowledgeRetrievalEvaluationImportPreviewVo();
        if (items == null || items.isEmpty()) {
            addImportIssue(response, 0, "EMPTY_IMPORT", "No cases were supplied.");
            return response;
        }
        Set<String> effectiveBaseIds = effectiveKnowledgeBaseIds(set.getAgentDefinitionId());
        for (int index = 0; index < items.size(); index++) {
            int issueCount = response.getIssues().size();
            KnowledgeRetrievalEvaluationCaseTransferVo transfer = items.get(index);
            KnowledgeRetrievalEvaluationCaseEntity item = transfer == null ? null : transfer.getItem();
            if (item == null || StringUtils.isBlank(item.getQuestion())) {
                addImportIssue(response, index + 1, "EMPTY_QUESTION", "Question is required.");
                continue;
            }
            List<KnowledgeRetrievalEvaluationLabel> labels = transfer.getLabels();
            if (labels == null || labels.isEmpty()) labels = Collections.singletonList(legacyLabel(item));
            String type = null;
            for (KnowledgeRetrievalEvaluationLabel label : labels) {
                String current = StringUtils.upperCase(label.getTargetType());
                if (!"DOCUMENT".equals(current) && !"SECTION".equals(current) && !"CHUNK".equals(current) || StringUtils.isBlank(label.getDocumentId()) || "SECTION".equals(current) && StringUtils.isBlank(label.getSectionPath()) || "CHUNK".equals(current) && StringUtils.isBlank(label.getChunkId())) {
                    addImportIssue(response, index + 1, "INVALID_LABEL", "A label target is invalid.");
                    break;
                }
                if (type != null && !type.equals(current)) {
                    addImportIssue(response, index + 1, "MIXED_TARGET_TYPES", "All labels must use the same target type.");
                    break;
                }
                type = current;
                KnowledgeDocument document = documentService.getById(label.getDocumentId());
                if (document == null || Boolean.TRUE.equals(document.getDeleted())) {
                    addImportIssue(response, index + 1, "DOCUMENT_UNAVAILABLE", "A labelled document is unavailable.");
                    break;
                }
                if (!effectiveBaseIds.contains(document.getKnowledgeBaseId())) {
                    addImportIssue(response, index + 1, "DOCUMENT_OUT_OF_SCOPE", "A labelled document is outside the agent retrieval scope.");
                    break;
                }
                if (resolveTargetChunks(current, label.getDocumentId(), label.getSectionPath(), label.getChunkId()).isEmpty()) {
                    addImportIssue(response, index + 1, "TARGET_NOT_RESOLVED", "A labelled document, section, or chunk is unavailable.");
                    break;
                }
            }
            if (response.getIssues().size() == issueCount) response.setAcceptedCount(response.getAcceptedCount() + 1);
        }
        return response;
    }

    /**
     * 新增ImportIssue。
     */
    private void addImportIssue(KnowledgeRetrievalEvaluationImportPreviewVo response, int row, String code, String message) {
        KnowledgeRetrievalEvaluationImportPreviewVo.RowIssue issue = new KnowledgeRetrievalEvaluationImportPreviewVo.RowIssue();
        issue.setRow(row);
        issue.setCode(code);
        issue.setMessage(message);
        response.getIssues().add(issue);
        response.setValid(false);
    }

    /**
     * 处理require运行。
     */
    private KnowledgeRetrievalEvaluationRun requireRun(String setId, String runId) {
        KnowledgeRetrievalEvaluationRun run = runMapper.selectById(runId);
        if (run == null || Boolean.TRUE.equals(run.getDeleted()) || !setId.equals(run.getEvaluationSetId()))
            throw new ServerException(404, I18nUtils.getMessage("knowledge.evaluation.run.not-found"));
        return run;
    }

    /**
     * 判断是否为Completed运行。
     */
    private boolean isCompletedRun(KnowledgeRetrievalEvaluationRun run) {
        return "SUCCEEDED".equals(run.getStatus()) || "PARTIAL_FAILED".equals(run.getStatus());
    }

    /**
     * 处理delta。
     */
    private Double delta(Double candidate, Double baseline) {
        return candidate == null || baseline == null ? null : candidate - baseline;
    }

    /**
     * 文档Title。
     */
    private String documentTitle(String documentId) {
        KnowledgeDocument document = StringUtils.isBlank(documentId) ? null : documentService.getById(documentId);
        return document == null ? null : document.getTitle();
    }

    /**
     * 处理requireHealthy。
     */
    private void requireHealthy(String setId) {
        KnowledgeRetrievalEvaluationHealthVo health = evaluationSetHealth(setId);
        if (!health.isHealthy())
            throw new ServerException(409, I18nUtils.getMessage("knowledge.evaluation.set.health.blocked"));
    }

    /**
     * Checks targets against the currently effective agent retrieval scope.
     */
    private KnowledgeRetrievalEvaluationHealthVo evaluationSetHealth(String setId) {
        KnowledgeRetrievalEvaluationSet set = setMapper.selectById(setId);
        if (set == null || Boolean.TRUE.equals(set.getDeleted()))
            throw new ServerException(404, I18nUtils.getMessage("knowledge.evaluation.set.not-found"));
        KnowledgeRetrievalEvaluationHealthVo response = new KnowledgeRetrievalEvaluationHealthVo();
        List<KnowledgeRetrievalEvaluationCaseEntity> cases = caseMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationCaseEntity.class).eq(KnowledgeRetrievalEvaluationCaseEntity::getEvaluationSetId, setId).eq(KnowledgeRetrievalEvaluationCaseEntity::getDeleted, false).eq(KnowledgeRetrievalEvaluationCaseEntity::getStatus, 1));
        response.setEnabledCaseCount(cases.size());
        if (cases.isEmpty())
            addHealthIssue(response, "ERROR", "NO_ENABLED_CASES", null, "The evaluation set has no enabled cases.");
        Set<String> effectiveBaseIds = effectiveKnowledgeBaseIds(set.getAgentDefinitionId());
        for (KnowledgeRetrievalEvaluationCaseEntity item : cases) {
            if (StringUtils.isBlank(item.getQuestion())) {
                addHealthIssue(response, "ERROR", "EMPTY_QUESTION", item.getId(), "The question is empty.");
                continue;
            }
            List<KnowledgeRetrievalEvaluationLabel> labels = labelMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationLabel.class).eq(KnowledgeRetrievalEvaluationLabel::getEvaluationCaseId, item.getId()).eq(KnowledgeRetrievalEvaluationLabel::getDeleted, false).eq(KnowledgeRetrievalEvaluationLabel::getStatus, 1));
            if (labels.isEmpty()) labels = Collections.singletonList(legacyLabel(item));
            String targetType = null;
            for (KnowledgeRetrievalEvaluationLabel label : labels) {
                String currentType = StringUtils.upperCase(label.getTargetType());
                if (!"DOCUMENT".equals(currentType) && !"SECTION".equals(currentType) && !"CHUNK".equals(currentType)) {
                    addHealthIssue(response, "ERROR", "INVALID_TARGET_TYPE", item.getId(), "The target type must be DOCUMENT, SECTION, or CHUNK.");
                    continue;
                }
                if (targetType != null && !targetType.equals(currentType)) {
                    addHealthIssue(response, "ERROR", "MIXED_TARGET_TYPES", item.getId(), "All labels for a case must use the same target type.");
                    continue;
                }
                targetType = currentType;
                KnowledgeDocument document = StringUtils.isBlank(label.getDocumentId()) ? null : documentService.getById(label.getDocumentId());
                if (document == null || Boolean.TRUE.equals(document.getDeleted())) {
                    addHealthIssue(response, "ERROR", "DOCUMENT_UNAVAILABLE", item.getId(), "A labelled document is unavailable.");
                    continue;
                }
                if (!effectiveBaseIds.contains(document.getKnowledgeBaseId())) {
                    addHealthIssue(response, "ERROR", "DOCUMENT_OUT_OF_SCOPE", item.getId(), "A labelled document is outside the agent retrieval scope.");
                    continue;
                }
                if (resolveTargetChunks(currentType, label.getDocumentId(), label.getSectionPath(), label.getChunkId()).isEmpty())
                    addHealthIssue(response, "ERROR", "TARGET_NOT_RESOLVED", item.getId(), "A labelled document, section, or chunk is unavailable.");
            }
        }
        return response;
    }

    /**
     * 处理legacyLabel。
     */
    private KnowledgeRetrievalEvaluationLabel legacyLabel(KnowledgeRetrievalEvaluationCaseEntity item) {
        KnowledgeRetrievalEvaluationLabel label = new KnowledgeRetrievalEvaluationLabel();
        label.setDocumentId(item.getDocumentId());
        label.setSectionPath(item.getSectionPath());
        label.setChunkId(item.getChunkId());
        label.setTargetType(StringUtils.upperCase(StringUtils.defaultIfBlank(item.getTargetType(), StringUtils.isNotBlank(item.getSectionPath()) ? "SECTION" : "DOCUMENT")));
        return label;
    }

    /**
     * 处理effective知识库BaseIds。
     */
    private Set<String> effectiveKnowledgeBaseIds(String agentDefinitionId) {
        Set<String> boundIds = bindingService.list(Wrappers.lambdaQuery(AgentKnowledgeBaseBinding.class).eq(AgentKnowledgeBaseBinding::getAgentDefinitionId, agentDefinitionId).eq(AgentKnowledgeBaseBinding::getStatus, 1).eq(AgentKnowledgeBaseBinding::getDeleted, false)).stream().map(AgentKnowledgeBaseBinding::getKnowledgeBaseId).filter(StringUtils::isNotBlank).collect(Collectors.toSet());
        Set<String> ids = boundIds.isEmpty() ? new HashSet<>() : knowledgeBaseService.list(Wrappers.lambdaQuery(KnowledgeBase.class).in(KnowledgeBase::getId, boundIds).eq(KnowledgeBase::getStatus, 1).eq(KnowledgeBase::getIndexStatus, 2).eq(KnowledgeBase::getDeleted, false)).stream().map(KnowledgeBase::getId).collect(Collectors.toSet());
        ids.addAll(knowledgeBaseService.list(Wrappers.lambdaQuery(KnowledgeBase.class).eq(KnowledgeBase::getScope, "PLATFORM").eq(KnowledgeBase::getStatus, 1).eq(KnowledgeBase::getIndexStatus, 2).eq(KnowledgeBase::getDeleted, false)).stream().map(KnowledgeBase::getId).collect(Collectors.toSet()));
        return ids;
    }

    /**
     * 新增HealthIssue。
     */
    private void addHealthIssue(KnowledgeRetrievalEvaluationHealthVo response, String severity, String code, String caseId, String message) {
        KnowledgeRetrievalEvaluationHealthVo.Issue issue = new KnowledgeRetrievalEvaluationHealthVo.Issue();
        issue.setSeverity(severity);
        issue.setCode(code);
        issue.setEvaluationCaseId(caseId);
        issue.setMessage(message);
        response.getIssues().add(issue);
        if ("ERROR".equals(severity)) response.setHealthy(false);
    }

    /**
     * Captures the effective retriever inputs without serializing credentials.
     */
    private String retrievalConfigSnapshot(String agentDefinitionId,
                                           List<KnowledgeRetrievalEvaluationCaseEntity> cases,
                                           List<KnowledgeRetrievalEvaluationLabel> labels) {
        List<AgentKnowledgeBaseBinding> bindings = bindingService.list(Wrappers.lambdaQuery(AgentKnowledgeBaseBinding.class).eq(AgentKnowledgeBaseBinding::getAgentDefinitionId, agentDefinitionId).eq(AgentKnowledgeBaseBinding::getDeleted, false));
        Set<String> labelledCaseIds = labels.stream().map(KnowledgeRetrievalEvaluationLabel::getEvaluationCaseId).filter(StringUtils::isNotBlank).collect(Collectors.toSet());
        Set<String> documentIds = new HashSet<>();
        for (KnowledgeRetrievalEvaluationLabel label : labels)
            if (StringUtils.isNotBlank(label.getDocumentId())) documentIds.add(label.getDocumentId());
        for (KnowledgeRetrievalEvaluationCaseEntity item : cases)
            if (!labelledCaseIds.contains(item.getId()) && StringUtils.isNotBlank(item.getDocumentId())) documentIds.add(item.getDocumentId());
        Set<String> targetBaseIds = documentIds.isEmpty() ? Collections.emptySet() : documentService.list(Wrappers.lambdaQuery(KnowledgeDocument.class)
                        .in(KnowledgeDocument::getId, documentIds)
                        .eq(KnowledgeDocument::getDeleted, false))
                .stream().map(KnowledgeDocument::getKnowledgeBaseId).filter(StringUtils::isNotBlank).collect(Collectors.toSet());
        List<KnowledgeBase> bases = targetBaseIds.isEmpty() ? Collections.emptyList() : knowledgeBaseService.list(Wrappers.lambdaQuery(KnowledgeBase.class)
                .in(KnowledgeBase::getId, targetBaseIds)
                .eq(KnowledgeBase::getDeleted, false));
        List<Map<String, Object>> baseSnapshots = new ArrayList<>();
        Set<String> modelIds = new HashSet<>();
        for (KnowledgeBase base : bases) {
                String embeddingModelId = base.getEmbeddingModelId();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", base.getId());
                item.put("name", base.getName());
                item.put("scope", base.getScope());
                item.put("status", base.getStatus());
                item.put("indexStatus", base.getIndexStatus());
                item.put("embeddingModelId", embeddingModelId);
                com.alibaba.fastjson2.JSONObject retrievalConfig;
                try {
                    retrievalConfig = JSON.parseObject(StringUtils.defaultIfBlank(base.getRetrievalConfig(), "{}"));
                } catch (Exception ignored) {
                    retrievalConfig = new com.alibaba.fastjson2.JSONObject();
                    retrievalConfig.put("raw", base.getRetrievalConfig());
                }
                retrievalConfig = effectiveRetrievalConfig(retrievalConfig);
                item.put("retrievalConfig", retrievalConfig);
                baseSnapshots.add(item);
                if (StringUtils.isNotBlank(embeddingModelId)) modelIds.add(embeddingModelId);
                String rerankModelId = retrievalConfig.getString("rerankModelId");
                if (Boolean.TRUE.equals(retrievalConfig.getBoolean("rerankEnabled"))
                        && StringUtils.isNotBlank(rerankModelId)) modelIds.add(rerankModelId);
                String queryRewriteModelId = retrievalConfig.getString("queryRewriteModelId");
                if (Boolean.TRUE.equals(retrievalConfig.getBoolean("queryRewriteEnabled"))
                        && StringUtils.isNotBlank(queryRewriteModelId)) modelIds.add(queryRewriteModelId);
        }
        List<Map<String, Object>> modelSnapshots = new ArrayList<>();
        if (!modelIds.isEmpty()) for (ModelCatalog model : modelCatalogService.listByIds(modelIds)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", model.getId());
            item.put("name", model.getName());
            item.put("providerId", model.getProviderId());
            item.put("capabilities", model.getCapabilities());
            item.put("status", model.getStatus());
            modelSnapshots.add(item);
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 4);
        snapshot.put("capturedAt", System.currentTimeMillis());
        snapshot.put("agentDefinitionId", agentDefinitionId);
        snapshot.put("knowledgeBaseScope", "EVALUATION_TARGETS");
        snapshot.put("bindings", bindings.stream().filter(binding -> targetBaseIds.contains(binding.getKnowledgeBaseId())).map(binding -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("knowledgeBaseId", binding.getKnowledgeBaseId());
            item.put("status", binding.getStatus());
            return item;
        }).collect(Collectors.toList()));
        snapshot.put("knowledgeBases", baseSnapshots);
        snapshot.put("models", modelSnapshots);
        return JSON.toJSONString(snapshot);
    }

    /**
     * Persist the values actually applied by the retriever, including defaults,
     * so a run can be diagnosed without consulting the later live configuration.
     */
    private com.alibaba.fastjson2.JSONObject effectiveRetrievalConfig(com.alibaba.fastjson2.JSONObject configured) {
        com.alibaba.fastjson2.JSONObject effective = new com.alibaba.fastjson2.JSONObject();
        effective.put("topK", boundedInt(configured.getInteger("topK"), 6, 1, 20));
        Double configuredMinSimilarity = configured.getDouble("minSimilarity");
        if (configuredMinSimilarity == null) configuredMinSimilarity = configured.getDouble("scoreThreshold");
        effective.put("minSimilarity", boundedDouble(configuredMinSimilarity, 0.30D, -1D, 1D));
        effective.put("maxChunksPerDocument", boundedInt(configured.getInteger("maxChunksPerDocument"), 4, 1, 10));
        effective.put("hybridEnabled", configured.getBoolean("hybridEnabled") == null || configured.getBoolean("hybridEnabled"));
        effective.put("vectorWeight", boundedDouble(configured.getDouble("vectorWeight"), 0.70D, 0D, 1D));
        effective.put("minLexicalScore", boundedDouble(configured.getDouble("minLexicalScore"), 0.05D, 0D, 1D));
        effective.put("authorityScore", boundedDouble(configured.getDouble("authorityScore"), 0D, 0D, 1D));
        effective.put("authorityWeight", boundedDouble(configured.getDouble("authorityWeight"), 0D, 0D, 1D));
        effective.put("freshnessWeight", boundedDouble(configured.getDouble("freshnessWeight"), 0D, 0D, 1D));
        effective.put("rerankEnabled", Boolean.TRUE.equals(configured.getBoolean("rerankEnabled")));
        effective.put("rerankModelId", configured.getString("rerankModelId"));
        effective.put("rerankTopN", boundedInt(configured.getInteger("rerankTopN"), 6, 1, 20));
        effective.put("strictGrounding", Boolean.TRUE.equals(configured.getBoolean("strictGrounding")));
        effective.put("queryRewriteEnabled", Boolean.TRUE.equals(configured.getBoolean("queryRewriteEnabled")));
        effective.put("queryRewriteModelId", configured.getString("queryRewriteModelId"));
        return effective;
    }

    /**
     * 处理boundedInt。
     */
    private int boundedInt(Integer value, int defaultValue, int min, int max) {
        return Math.max(min, Math.min(max, value == null ? defaultValue : value));
    }

    /**
     * 处理boundedDouble。
     */
    private double boundedDouble(Double value, double defaultValue, double min, double max) {
        return Math.max(min, Math.min(max, value == null ? defaultValue : value));
    }

    /**
     * 解析TargetChunks。
     */
    private List<KnowledgeDocumentChunk> resolveTargetChunks(String targetType, String documentId, String sectionPath, String chunkId) {
        if (StringUtils.isBlank(documentId)) return Collections.emptyList();
        if ("CHUNK".equalsIgnoreCase(targetType))
            return StringUtils.isBlank(chunkId) ? Collections.<KnowledgeDocumentChunk>emptyList() : chunkService.list(Wrappers.lambdaQuery(KnowledgeDocumentChunk.class).eq(KnowledgeDocumentChunk::getId, chunkId).eq(KnowledgeDocumentChunk::getDocumentId, documentId).eq(KnowledgeDocumentChunk::getDeleted, false));
        if ("SECTION".equalsIgnoreCase(targetType) && StringUtils.isBlank(sectionPath)) return Collections.emptyList();
        return chunkService.list(Wrappers.lambdaQuery(KnowledgeDocumentChunk.class).eq(KnowledgeDocumentChunk::getDocumentId, documentId).eq(KnowledgeDocumentChunk::getDeleted, false).eq("SECTION".equalsIgnoreCase(targetType), KnowledgeDocumentChunk::getSectionPath, sectionPath));
    }
}
