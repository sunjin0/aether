package com.aether.knowledge.controller;

import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.knowledge.model.KnowledgeRetrievalEvaluationCase;
import com.aether.knowledge.model.KnowledgeRetrievalEvaluationReport;
import com.aether.knowledge.service.KnowledgeRetrievalEvaluationService;
import com.aether.knowledge.entity.KnowledgeRetrievalEvaluationSet;
import com.aether.knowledge.entity.KnowledgeRetrievalEvaluationCaseEntity;
import com.aether.knowledge.mapper.KnowledgeRetrievalEvaluationSetMapper;
import com.aether.knowledge.mapper.KnowledgeRetrievalEvaluationCaseMapper;
import com.aether.knowledge.mapper.KnowledgeRetrievalEvaluationRunMapper;
import com.aether.knowledge.mapper.KnowledgeRetrievalEvaluationResultMapper;
import com.aether.knowledge.entity.KnowledgeRetrievalEvaluationRun;
import com.aether.knowledge.entity.KnowledgeRetrievalEvaluationResult;
import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.service.KnowledgeDocumentChunkService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.service.KnowledgeAccessService;
import com.aether.knowledge.vo.KnowledgeRetrievalEvaluationResultVo;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aether.permission.Permission;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.stream.Collectors;

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
    private final KnowledgeDocumentChunkService chunkService;
    private final KnowledgeDocumentService documentService;
    private final KnowledgeAccessService knowledgeAccessService;

    public KnowledgeRetrievalEvaluationController(KnowledgeRetrievalEvaluationService evaluationService,
                                                  KnowledgeRetrievalEvaluationSetMapper setMapper,
                                                  KnowledgeRetrievalEvaluationCaseMapper caseMapper,
                                                  KnowledgeRetrievalEvaluationRunMapper runMapper,
                                                  KnowledgeRetrievalEvaluationResultMapper resultMapper,
                                                  KnowledgeDocumentChunkService chunkService,
                                                  KnowledgeDocumentService documentService,
                                                  KnowledgeAccessService knowledgeAccessService) {
        this.evaluationService = evaluationService;
        this.setMapper = setMapper;
        this.caseMapper = caseMapper;
        this.runMapper = runMapper;
        this.resultMapper = resultMapper;
        this.chunkService = chunkService;
        this.documentService = documentService;
        this.knowledgeAccessService = knowledgeAccessService;
    }
    @GetMapping("/sets") public WebResponse<List<KnowledgeRetrievalEvaluationSet>> sets() { return WebResponse.OK(setMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationSet.class).eq(KnowledgeRetrievalEvaluationSet::getDeleted, false).orderByDesc(KnowledgeRetrievalEvaluationSet::getCreatedAt))); }
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write) @PostMapping("/sets") public WebResponse<String> saveSet(@RequestBody KnowledgeRetrievalEvaluationSet set) { if (StringUtils.isBlank(set.getAgentDefinitionId()) || StringUtils.isBlank(set.getName())) throw new ServerException(400, "agentDefinitionId and name are required"); if (set.getStatus()==null) set.setStatus(1); setMapper.insert(set); return WebResponse.OK(set.getId()); }
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write) @PutMapping("/sets/{id}") public WebResponse<Void> updateSet(@PathVariable String id, @RequestBody KnowledgeRetrievalEvaluationSet set) { set.setId(id); setMapper.updateById(set); return WebResponse.OK("OK"); }
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write) @DeleteMapping("/sets/{id}") public WebResponse<Void> deleteSet(@PathVariable String id) { KnowledgeRetrievalEvaluationSet set=new KnowledgeRetrievalEvaluationSet(); set.setId(id); set.setDeleted(true); setMapper.updateById(set); return WebResponse.OK("OK"); }
    @GetMapping("/sets/{id}/cases") public WebResponse<List<KnowledgeRetrievalEvaluationCaseEntity>> cases(@PathVariable String id) { return WebResponse.OK(caseMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationCaseEntity.class).eq(KnowledgeRetrievalEvaluationCaseEntity::getEvaluationSetId,id).eq(KnowledgeRetrievalEvaluationCaseEntity::getDeleted,false))); }
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write) @PostMapping("/sets/{id}/cases") public WebResponse<String> saveCase(@PathVariable String id,@RequestBody KnowledgeRetrievalEvaluationCaseEntity item) { if(StringUtils.isBlank(item.getQuestion())) throw new ServerException(400,"question is required"); item.setEvaluationSetId(id); if(item.getStatus()==null)item.setStatus(1); caseMapper.insert(item); return WebResponse.OK(item.getId()); }
    @Permission(path = "/knowledge/evaluation", type = Permission.Type.Write) @PostMapping("/sets/{id}/run") public WebResponse<KnowledgeRetrievalEvaluationReport> runSet(@PathVariable String id) {
        KnowledgeRetrievalEvaluationSet set=setMapper.selectById(id); if(set==null||Boolean.TRUE.equals(set.getDeleted())) throw new ServerException(404,"evaluation set not found");
        List<KnowledgeRetrievalEvaluationCaseEntity> cases=caseMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationCaseEntity.class).eq(KnowledgeRetrievalEvaluationCaseEntity::getEvaluationSetId,id).eq(KnowledgeRetrievalEvaluationCaseEntity::getDeleted,false).eq(KnowledgeRetrievalEvaluationCaseEntity::getStatus,1));
        List<KnowledgeRetrievalEvaluationCase> inputs=new java.util.ArrayList<>(); List<KnowledgeRetrievalEvaluationCaseEntity> validCases=new java.util.ArrayList<>(); int invalid=0;
        for(KnowledgeRetrievalEvaluationCaseEntity item:cases){ if(StringUtils.isBlank(item.getDocumentId())) { invalid++; continue; } List<KnowledgeDocumentChunk> chunks=chunkService.list(Wrappers.lambdaQuery(KnowledgeDocumentChunk.class).eq(KnowledgeDocumentChunk::getDocumentId,item.getDocumentId()).eq(KnowledgeDocumentChunk::getDeleted,false).eq(StringUtils.isNotBlank(item.getSectionPath()),KnowledgeDocumentChunk::getSectionPath,item.getSectionPath())); if(chunks.isEmpty()) { invalid++; continue; } KnowledgeRetrievalEvaluationCase input=new KnowledgeRetrievalEvaluationCase(); input.setQuestion(item.getQuestion()); input.setExpectedChunkIds(chunks.stream().map(KnowledgeDocumentChunk::getId).collect(java.util.stream.Collectors.toList())); inputs.add(input); validCases.add(item); }
        KnowledgeRetrievalEvaluationReport report=evaluationService.evaluate(set.getAgentDefinitionId(),inputs); KnowledgeRetrievalEvaluationRun run=new KnowledgeRetrievalEvaluationRun(); run.setEvaluationSetId(id); run.setTotalCount(report.getTotal()); run.setInvalidCount(invalid); run.setRecallAtK(report.getRecallAtK()); run.setMrr(report.getMrr()); run.setNdcg(report.getNdcg()); run.setStartedAt(System.currentTimeMillis()); run.setFinishedAt(System.currentTimeMillis()); runMapper.insert(run);
        for(int i=0;i<report.getItems().size();i++){ KnowledgeRetrievalEvaluationReport.Item item=report.getItems().get(i); KnowledgeRetrievalEvaluationResult result=new KnowledgeRetrievalEvaluationResult(); result.setRunId(run.getId()); result.setEvaluationCaseId(validCases.get(i).getId()); result.setStatus("EVALUATED"); result.setRetrievedChunkIds(JSON.toJSONString(item.getRetrievedChunkIds())); result.setRecallAtK(item.getRecallAtK()); result.setMrr(item.getMrr()); result.setNdcg(item.getNdcg()); resultMapper.insert(result); }
        return WebResponse.OK(report);
    }
    @GetMapping("/sets/{id}/runs") public WebResponse<List<KnowledgeRetrievalEvaluationRun>> runs(@PathVariable String id){ return WebResponse.OK(runMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationRun.class).eq(KnowledgeRetrievalEvaluationRun::getEvaluationSetId,id).eq(KnowledgeRetrievalEvaluationRun::getDeleted,false).orderByDesc(KnowledgeRetrievalEvaluationRun::getStartedAt))); }

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

    @ApiOperation("文档可标注章节")
    @Permission(required = false)
    @GetMapping("/documents/{id}/sections")
    public WebResponse<List<String>> sections(@PathVariable String id) {
        KnowledgeDocument document = documentService.getById(id);
        if (document == null || Boolean.TRUE.equals(document.getDeleted())) throw new ServerException(404, "document not found");
        knowledgeAccessService.requireReadable(document.getKnowledgeBaseId());
        List<String> sections = chunkService.list(Wrappers.lambdaQuery(KnowledgeDocumentChunk.class)
                        .eq(KnowledgeDocumentChunk::getDocumentId, id)
                        .eq(KnowledgeDocumentChunk::getDeleted, false)
                        .isNotNull(KnowledgeDocumentChunk::getSectionPath)
                        .ne(KnowledgeDocumentChunk::getSectionPath, ""))
                .stream().map(KnowledgeDocumentChunk::getSectionPath).distinct().sorted().collect(Collectors.toList());
        return WebResponse.OK(sections);
    }

    @ApiOperation("单次运行逐题结果")
    @GetMapping("/sets/{setId}/runs/{runId}/results")
    public WebResponse<List<KnowledgeRetrievalEvaluationResultVo>> results(@PathVariable String setId, @PathVariable String runId) {
        KnowledgeRetrievalEvaluationRun run = runMapper.selectById(runId);
        if (run == null || Boolean.TRUE.equals(run.getDeleted()) || !setId.equals(run.getEvaluationSetId())) throw new ServerException(404, "evaluation run not found");
        List<KnowledgeRetrievalEvaluationResult> results = resultMapper.selectList(Wrappers.lambdaQuery(KnowledgeRetrievalEvaluationResult.class)
                .eq(KnowledgeRetrievalEvaluationResult::getRunId, runId).eq(KnowledgeRetrievalEvaluationResult::getDeleted, false));
        if (results.isEmpty()) return WebResponse.OK(Collections.emptyList());
        Set<String> caseIds = results.stream().map(KnowledgeRetrievalEvaluationResult::getEvaluationCaseId).collect(Collectors.toSet());
        Map<String, KnowledgeRetrievalEvaluationCaseEntity> cases = caseMapper.selectBatchIds(caseIds).stream()
                .collect(Collectors.toMap(KnowledgeRetrievalEvaluationCaseEntity::getId, item -> item));
        Set<String> chunkIds = new HashSet<>();
        for (KnowledgeRetrievalEvaluationResult result : results) chunkIds.addAll(JSON.parseArray(result.getRetrievedChunkIds(), String.class));
        Map<String, KnowledgeDocumentChunk> chunks = chunkIds.isEmpty() ? Collections.emptyMap() : chunkService.listByIds(chunkIds).stream()
                .collect(Collectors.toMap(KnowledgeDocumentChunk::getId, item -> item));
        Set<String> documentIds = new HashSet<>();
        for (KnowledgeRetrievalEvaluationCaseEntity item : cases.values()) if (StringUtils.isNotBlank(item.getDocumentId())) documentIds.add(item.getDocumentId());
        for (KnowledgeDocumentChunk chunk : chunks.values()) if (StringUtils.isNotBlank(chunk.getDocumentId())) documentIds.add(chunk.getDocumentId());
        Map<String, String> titles = documentIds.isEmpty() ? Collections.emptyMap() : documentService.listByIds(documentIds).stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, KnowledgeDocument::getTitle));
        List<KnowledgeRetrievalEvaluationResultVo> response = new ArrayList<>();
        for (KnowledgeRetrievalEvaluationResult result : results) {
            KnowledgeRetrievalEvaluationCaseEntity evaluationCase = cases.get(result.getEvaluationCaseId());
            if (evaluationCase == null) continue;
            KnowledgeRetrievalEvaluationResultVo item = new KnowledgeRetrievalEvaluationResultVo();
            item.setId(result.getId()); item.setEvaluationCaseId(result.getEvaluationCaseId()); item.setQuestion(evaluationCase.getQuestion());
            item.setExpectedDocumentId(evaluationCase.getDocumentId()); item.setExpectedDocumentTitle(titles.get(evaluationCase.getDocumentId()));
            item.setExpectedSectionPath(evaluationCase.getSectionPath()); item.setStatus(result.getStatus());
            item.setRecallAtK(result.getRecallAtK()); item.setMrr(result.getMrr()); item.setNdcg(result.getNdcg());
            List<KnowledgeRetrievalEvaluationResultVo.RetrievedChunk> retrieved = new ArrayList<>();
            List<String> retrievedIds = JSON.parseArray(result.getRetrievedChunkIds(), String.class);
            for (int index = 0; index < retrievedIds.size(); index++) {
                KnowledgeDocumentChunk chunk = chunks.get(retrievedIds.get(index)); if (chunk == null) continue;
                KnowledgeRetrievalEvaluationResultVo.RetrievedChunk source = new KnowledgeRetrievalEvaluationResultVo.RetrievedChunk();
                source.setId(chunk.getId()); source.setDocumentId(chunk.getDocumentId()); source.setDocumentTitle(titles.get(chunk.getDocumentId()));
                source.setSectionPath(chunk.getSectionPath()); source.setChunkIndex(chunk.getChunkIndex()); source.setRank(index + 1); retrieved.add(source);
            }
            item.setRetrievedChunks(retrieved); response.add(item);
        }
        return WebResponse.OK(response);
    }
    @ApiOperation("批量评测知识库检索命中率")
    @Permission(path = "/knowledge/base", type = Permission.Type.Write)
    @PostMapping("/run")
    public WebResponse<KnowledgeRetrievalEvaluationReport> run(@RequestBody Request request) {
        if (request == null || StringUtils.isBlank(request.getAgentDefinitionId())) {
            throw new ServerException(400, "agentDefinitionId is required");
        }
        return WebResponse.OK(evaluationService.evaluate(request.getAgentDefinitionId(), request.getCases()));
    }
    public static class Request {
        private String agentDefinitionId;
        private List<KnowledgeRetrievalEvaluationCase> cases;
        public String getAgentDefinitionId() { return agentDefinitionId; }
        public void setAgentDefinitionId(String agentDefinitionId) { this.agentDefinitionId = agentDefinitionId; }
        public List<KnowledgeRetrievalEvaluationCase> getCases() { return cases; }
        public void setCases(List<KnowledgeRetrievalEvaluationCase> cases) { this.cases = cases; }
    }
}
