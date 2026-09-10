package com.aether.evaluation.controller;

import com.aether.entity.WebResponse;
import com.aether.evaluation.entity.EvaluationDataset;
import com.aether.evaluation.service.EvaluationDatasetService;
import com.aether.evaluation.service.EvaluationCaseService;
import com.aether.evaluation.service.EvaluationDatasetVersionService;
import com.aether.evaluation.service.EvaluationCaseVersionService;
import com.aether.evaluation.service.EvaluationEvaluatorVersionService;
import com.aether.evaluation.service.EvaluationDataDeletionService;
import com.aether.evaluation.entity.EvaluationCase;
import com.aether.evaluation.entity.EvaluationDatasetVersion;
import com.aether.evaluation.entity.EvaluationCaseVersion;
import com.aether.permission.Permission;
import com.aether.i18n.I18nUtils;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.validation.Valid;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Api(tags = "统一评测集 API")
@RestController
@RequestMapping("/api/evaluation/datasets")
@Permission(path = "/evaluation/datasets")
public class EvaluationDatasetController {
    private final EvaluationDatasetService service;
    private final EvaluationCaseService caseService;
    private final EvaluationDatasetVersionService versionService;
    private final EvaluationCaseVersionService caseVersionService;
    private final EvaluationEvaluatorVersionService evaluatorVersionService;
    private final EvaluationDataDeletionService deletionService;

    public EvaluationDatasetController(EvaluationDatasetService service, EvaluationCaseService caseService,
                                       EvaluationDatasetVersionService versionService, EvaluationCaseVersionService caseVersionService,
                                       EvaluationEvaluatorVersionService evaluatorVersionService, EvaluationDataDeletionService deletionService) {
        this.service = service; this.caseService = caseService; this.versionService = versionService; this.caseVersionService = caseVersionService; this.evaluatorVersionService = evaluatorVersionService; this.deletionService = deletionService;
    }

    @GetMapping
    public WebResponse<List<EvaluationDataset>> list(@RequestParam(required = false) String targetType) {
        return WebResponse.OK(service.lambdaQuery().eq(targetType != null, EvaluationDataset::getTargetType, targetType)
                .eq(EvaluationDataset::getArchived, false).orderByDesc(EvaluationDataset::getUpdatedAt).list());
    }

    @PostMapping
    @Permission(path = "/evaluation/datasets", type = Permission.Type.Write)
    public WebResponse<String> create(@Valid @RequestBody EvaluationDataset dataset) {
        if (dataset == null || dataset.getName() == null || dataset.getName().trim().isEmpty())
            return WebResponse.Error(422, I18nUtils.getMessage("agent.evaluation.dataset.name.required"));
        if (!"AGENT".equals(dataset.getTargetType()) && !"WORKFLOW".equals(dataset.getTargetType()))
            return WebResponse.Error(422, I18nUtils.getMessage("agent.evaluation.target-type.invalid"));
        dataset.setId(null);
        dataset.setRevision(0L);
        dataset.setArchived(false);
        service.save(dataset);
        return WebResponse.OK(null, dataset.getId());
    }

    @PutMapping("/{id}")
    @Permission(path = "/evaluation/datasets", type = Permission.Type.Write)
    public WebResponse<?> update(@PathVariable String id, @RequestBody EvaluationDataset request) {
        EvaluationDataset current = service.getById(id);
        if (current == null) return WebResponse.Error(404, I18nUtils.getMessage("agent.evaluation.dataset.not-found"));
        if (request.getName() != null) current.setName(request.getName());
        if (request.getDescription() != null) current.setDescription(request.getDescription());
        current.setRevision((current.getRevision() == null ? 0L : current.getRevision()) + 1);
        service.updateById(current);
        return WebResponse.OK(null);
    }

    @DeleteMapping("/{id}")
    @Permission(path = "/evaluation/datasets", type = Permission.Type.Write)
    public WebResponse<?> delete(@PathVariable String id) {
        if (service.getById(id) == null) return WebResponse.Error(404, I18nUtils.getMessage("agent.evaluation.dataset.not-found"));
        deletionService.deleteDataset(id);
        return WebResponse.OK(I18nUtils.getMessage("agent.evaluation.data.delete.success"));
    }

    @GetMapping("/{id}/cases")
    public WebResponse<List<EvaluationCase>> cases(@PathVariable String id) {
        return WebResponse.OK(caseService.lambdaQuery().eq(EvaluationCase::getDatasetId, id)
                .eq(EvaluationCase::getDeleted, false).orderByAsc(EvaluationCase::getSortNum).list());
    }

    @GetMapping("/{id}/cases/export")
    public WebResponse<?> exportCases(@PathVariable String id) {
        EvaluationDataset dataset=service.getById(id);if(dataset==null)return WebResponse.Error(404,I18nUtils.getMessage("agent.evaluation.dataset.not-found"));
        Map<String,Object> out=new LinkedHashMap<>();out.put("schemaVersion",1);out.put("targetType",dataset.getTargetType());out.put("cases",caseService.lambdaQuery().eq(EvaluationCase::getDatasetId,id).eq(EvaluationCase::getDeleted,false).orderByAsc(EvaluationCase::getSortNum).list());return WebResponse.OK(out);
    }

    @PostMapping("/{id}/cases/import/preview")
    @Permission(path = "/evaluation/datasets", type = Permission.Type.Write)
    public WebResponse<?> previewImport(@PathVariable String id,@RequestBody Map<String,Object> request) {
        EvaluationDataset dataset=service.getById(id);if(dataset==null)return WebResponse.Error(404,I18nUtils.getMessage("agent.evaluation.dataset.not-found"));return WebResponse.OK(validateImport(id,request));
    }

    @PostMapping("/{id}/cases/import")
    @Permission(path = "/evaluation/datasets", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<?> importCases(@PathVariable String id,@RequestBody Map<String,Object> request) {
        EvaluationDataset dataset=service.getById(id);if(dataset==null)return WebResponse.Error(404,I18nUtils.getMessage("agent.evaluation.dataset.not-found"));Map<String,Object> preview=validateImport(id,request);if(!Boolean.TRUE.equals(preview.get("valid")))return WebResponse.Error(422,I18nUtils.getMessage("agent.evaluation.case.import.invalid"));
        @SuppressWarnings("unchecked") List<Object> items=(List<Object>)preview.get("items");int sort=caseService.lambdaQuery().eq(EvaluationCase::getDatasetId,id).count().intValue();for(Object raw:items){EvaluationCase item=com.alibaba.fastjson2.JSON.parseObject(com.alibaba.fastjson2.JSON.toJSONString(raw),EvaluationCase.class);item.setId(null);item.setDatasetId(id);item.setRevision(0L);item.setSortNum(++sort);if(item.getEnabled()==null)item.setEnabled(true);if(item.getPassThreshold()==null)item.setPassThreshold(80);caseService.save(item);}dataset.setRevision((dataset.getRevision()==null?0:dataset.getRevision())+1);service.updateById(dataset);return WebResponse.OK(I18nUtils.getMessage("agent.evaluation.case.import.success"),items.size());
    }

    @GetMapping("/{id}/versions")
    public WebResponse<List<EvaluationDatasetVersion>> versions(@PathVariable String id) {
        return WebResponse.OK(versionService.lambdaQuery().eq(EvaluationDatasetVersion::getDatasetId, id)
                .eq(EvaluationDatasetVersion::getDeleted, false).orderByDesc(EvaluationDatasetVersion::getVersionNo).list());
    }

    @PostMapping("/{id}/cases")
    @Permission(path = "/evaluation/datasets", type = Permission.Type.Write)
    public WebResponse<String> addCase(@PathVariable String id, @RequestBody EvaluationCase item) {
        if (service.getById(id) == null || item == null || blank(item.getCaseKey()) || blank(item.getInputJson()))
            return WebResponse.Error(422, I18nUtils.getMessage("agent.evaluation.case.input.invalid"));
        item.setId(null); item.setDatasetId(id); if (item.getEnabled() == null) item.setEnabled(true);
        if (item.getPassThreshold() == null) item.setPassThreshold(80);
        item.setRevision(0L); caseService.save(item); return WebResponse.OK(null, item.getId());
    }

    @PutMapping("/{id}/cases/{caseId}")
    @Permission(path = "/evaluation/datasets", type = Permission.Type.Write)
    public WebResponse<?> updateCase(@PathVariable String id, @PathVariable String caseId, @RequestBody EvaluationCase item) {
        EvaluationCase current=caseService.getById(caseId);
        if(current==null||!id.equals(current.getDatasetId()))return WebResponse.Error(404,I18nUtils.getMessage("agent.evaluation.case.not-found"));
        if(item==null||blank(item.getCaseKey())||blank(item.getInputJson()))return WebResponse.Error(422,I18nUtils.getMessage("agent.evaluation.case.input.invalid"));
        boolean duplicate=caseService.lambdaQuery().eq(EvaluationCase::getDatasetId,id).eq(EvaluationCase::getCaseKey,item.getCaseKey()).ne(EvaluationCase::getId,caseId).eq(EvaluationCase::getDeleted,false).exists();
        if(duplicate)return WebResponse.Error(422,I18nUtils.getMessage("agent.evaluation.case.key.duplicate"));
        item.setId(caseId);item.setDatasetId(id);item.setRevision((current.getRevision()==null?0:current.getRevision())+1);caseService.updateById(item);return WebResponse.OK(null);
    }

    @DeleteMapping("/{id}/cases/{caseId}")
    @Permission(path = "/evaluation/datasets", type = Permission.Type.Write)
    public WebResponse<?> deleteCase(@PathVariable String id, @PathVariable String caseId) {
        EvaluationCase current = caseService.getById(caseId);
        if (current == null || !id.equals(current.getDatasetId())) return WebResponse.Error(404, I18nUtils.getMessage("agent.evaluation.case.not-found"));
        caseService.removeById(caseId);
        return WebResponse.OK(I18nUtils.getMessage("agent.evaluation.data.delete.success"));
    }

    @PostMapping("/{id}/versions")
    @Permission(path = "/evaluation/datasets", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<?> publishVersion(@PathVariable String id) {
        EvaluationDataset dataset = service.getById(id);
        if (dataset == null) return WebResponse.Error(404, I18nUtils.getMessage("agent.evaluation.dataset.not-found"));
        List<EvaluationCase> enabled = caseService.lambdaQuery().eq(EvaluationCase::getDatasetId, id)
                .eq(EvaluationCase::getEnabled, true).eq(EvaluationCase::getDeleted, false).list();
        if (enabled.isEmpty()) return WebResponse.Error(422, I18nUtils.getMessage("agent.evaluation.dataset.enabled-case.required"));
        String bindingsError = validateBindings(enabled);
        if (bindingsError != null) return WebResponse.Error(422, I18nUtils.getMessage(bindingsError));
        String assertionsError = validateAssertions(dataset.getTargetType(), enabled);
        if (assertionsError != null) return WebResponse.Error(422, I18nUtils.getMessage(assertionsError));
        List<EvaluationDatasetVersion> versions = versionService.lambdaQuery().eq(EvaluationDatasetVersion::getDatasetId, id)
                .orderByDesc(EvaluationDatasetVersion::getVersionNo).last("LIMIT 1").list();
        Integer latest = versions.isEmpty() ? 0 : versions.get(0).getVersionNo();
        EvaluationDatasetVersion version = new EvaluationDatasetVersion(); version.setDatasetId(id);
        version.setVersionNo(latest + 1); version.setCaseCount(enabled.size()); version.setContentHash("pending");
        version.setPublishedAt(System.currentTimeMillis()); versionService.save(version);
        StringBuilder digestInput = new StringBuilder();
        for (EvaluationCase item : enabled) {
            EvaluationCaseVersion snapshot = new EvaluationCaseVersion(); snapshot.setDatasetVersionId(version.getId());
            snapshot.setCaseKey(item.getCaseKey()); snapshot.setName(item.getName()); snapshot.setInputJson(item.getInputJson());
            snapshot.setReferenceJson(item.getReferenceJson()); snapshot.setAssertionsJson(item.getAssertionsJson());
            snapshot.setEvaluatorBindingsJson(item.getEvaluatorBindingsJson()); snapshot.setTagsJson(item.getTagsJson());
            snapshot.setRequired(Boolean.TRUE.equals(item.getRequired())); snapshot.setPassThreshold(item.getPassThreshold() == null ? 80 : item.getPassThreshold());
            String content = String.join("\n", nullToEmpty(snapshot.getCaseKey()), nullToEmpty(snapshot.getInputJson()), nullToEmpty(snapshot.getReferenceJson()),
                    nullToEmpty(snapshot.getAssertionsJson()), nullToEmpty(snapshot.getEvaluatorBindingsJson()), nullToEmpty(snapshot.getTagsJson()),
                    String.valueOf(snapshot.getRequired()), String.valueOf(snapshot.getPassThreshold()));
            snapshot.setContentHash(sha256(content)); caseVersionService.save(snapshot); digestInput.append(content).append('\n');
        }
        version.setContentHash(sha256(digestInput.toString())); versionService.updateById(version);
        dataset.setRevision((dataset.getRevision() == null ? 0L : dataset.getRevision()) + 1); service.updateById(dataset);
        return WebResponse.OK(version.getVersionNo());
    }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    /** Published case versions must reference immutable evaluators, otherwise an experiment can never produce a score. */
    private String validateBindings(List<EvaluationCase> cases) {
        for (EvaluationCase item : cases) {
            com.alibaba.fastjson2.JSONArray bindings;
            try { bindings = com.alibaba.fastjson2.JSONArray.parseArray(item.getEvaluatorBindingsJson()); }
            catch (RuntimeException ex) { return "agent.evaluation.case.bindings.invalid"; }
            if (bindings == null || bindings.isEmpty()) return "agent.evaluation.case.bindings.required";
            for (int index = 0; index < bindings.size(); index++) {
                com.alibaba.fastjson2.JSONObject binding = bindings.getJSONObject(index);
                if (binding == null || blank(binding.getString("evaluatorVersionId"))) return "agent.evaluation.case.bindings.invalid";
                if (evaluatorVersionService.getById(binding.getString("evaluatorVersionId")) == null) return "agent.evaluation.case.evaluator-version.not-found";
                java.math.BigDecimal weight = binding.getBigDecimal("weight");
                if (weight != null && weight.signum() <= 0) return "agent.evaluation.case.bindings.weight.invalid";
            }
        }
        return null;
    }
    /** Assertion targets must use stable tool or node identifiers appropriate to the dataset target. */
    private String validateAssertions(String targetType, List<EvaluationCase> cases) {
        for (EvaluationCase item : cases) {
            if (blank(item.getAssertionsJson())) continue;
            com.alibaba.fastjson2.JSONArray assertions;
            try { assertions = com.alibaba.fastjson2.JSONArray.parseArray(item.getAssertionsJson()); }
            catch (RuntimeException ex) { return "agent.evaluation.case.assertions.invalid"; }
            if (assertions == null) return "agent.evaluation.case.assertions.invalid";
            for (int index = 0; index < assertions.size(); index++) {
                com.alibaba.fastjson2.JSONObject assertion = assertions.getJSONObject(index);
                if (assertion == null || blank(assertion.getString("target"))) return "agent.evaluation.case.assertions.invalid";
                String type = assertion.getString("type");
                boolean agentAssertion = "TOOL_CALLED".equals(type) || "TOOL_NOT_CALLED".equals(type) || "TOOL_SUCCEEDED".equals(type) || "TOOL_NOT_SUCCEEDED".equals(type);
                boolean workflowAssertion = "NODE_VISITED".equals(type) || "NODE_NOT_VISITED".equals(type) || "NODE_COMPLETED".equals(type) || "NODE_NOT_COMPLETED".equals(type);
                if ((!agentAssertion && !workflowAssertion)
                        || ("AGENT".equals(targetType) && !agentAssertion)
                        || ("WORKFLOW".equals(targetType) && !workflowAssertion)) return "agent.evaluation.case.assertions.invalid";
            }
        }
        return null;
    }
    private Map<String,Object> validateImport(String datasetId,Map<String,Object> request){
        List<Object> items=request==null||!(request.get("cases") instanceof List)?java.util.Collections.emptyList():(List<Object>)request.get("cases");Set<String> existing=new LinkedHashSet<>();for(EvaluationCase item:caseService.lambdaQuery().eq(EvaluationCase::getDatasetId,datasetId).eq(EvaluationCase::getDeleted,false).list())existing.add(item.getCaseKey());Set<String> seen=new LinkedHashSet<>();List<Map<String,Object>> errors=new ArrayList<>();
        for(int index=0;index<items.size();index++){EvaluationCase item;try{item=com.alibaba.fastjson2.JSON.parseObject(com.alibaba.fastjson2.JSON.toJSONString(items.get(index)),EvaluationCase.class);}catch(RuntimeException ex){errors.add(error(index,"INVALID_ITEM"));continue;}if(item==null||blank(item.getCaseKey())||blank(item.getInputJson()))errors.add(error(index,"CASE_KEY_OR_INPUT_REQUIRED"));else if(!seen.add(item.getCaseKey())||existing.contains(item.getCaseKey()))errors.add(error(index,"CASE_KEY_DUPLICATE"));else if(item.getPassThreshold()!=null&&(item.getPassThreshold()<0||item.getPassThreshold()>100))errors.add(error(index,"PASS_THRESHOLD_INVALID"));}
        Map<String,Object> out=new LinkedHashMap<>();out.put("valid",!items.isEmpty()&&errors.isEmpty());out.put("total",items.size());out.put("errors",errors);out.put("items",items);return out;
    }
    private Map<String,Object> error(int index,String code){Map<String,Object> error=new LinkedHashMap<>();error.put("index",index);error.put("code",code);return error;}
    private String nullToEmpty(String value) { return value == null ? "" : value; }
    private String sha256(String value) {
        try { byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(); for (byte b : bytes) result.append(String.format("%02x", b)); return result.toString();
        } catch (Exception ex) { throw new IllegalStateException(I18nUtils.getMessage("agent.evaluation.hash.failed"), ex); }
    }
}
