package com.aether.evaluation.controller;

import com.aether.entity.WebResponse;
import com.aether.evaluation.entity.EvaluationEvaluator;
import com.aether.evaluation.entity.EvaluationEvaluatorVersion;
import com.aether.evaluation.service.EvaluationDataDeletionService;
import com.aether.evaluation.service.EvaluationEvaluatorService;
import com.aether.evaluation.service.EvaluationEvaluatorVersionService;
import com.aether.evaluation.service.RuleEvaluationGrader;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.permission.Permission;
import com.aether.sys.service.AccountDataScopeService;
import io.swagger.annotations.Api;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;

@Api(tags = "评测评分器 API")
@RestController
@RequestMapping("/api/evaluation/evaluators")
@Permission(path = "/evaluation/evaluators")
public class EvaluationEvaluatorController {
    private final EvaluationEvaluatorService service;
    private final EvaluationEvaluatorVersionService versionService;
    private final EvaluationDataDeletionService deletionService;
    private final AccountDataScopeService dataScopeService;

    @Autowired
    public EvaluationEvaluatorController(EvaluationEvaluatorService service, EvaluationEvaluatorVersionService versionService, EvaluationDataDeletionService deletionService, AccountDataScopeService dataScopeService) {
        this.service = service; this.versionService = versionService; this.deletionService = deletionService; this.dataScopeService = dataScopeService;
    }
    public EvaluationEvaluatorController(EvaluationEvaluatorService service, EvaluationEvaluatorVersionService versionService, EvaluationDataDeletionService deletionService) {
        this(service, versionService, deletionService, null);
    }
    @GetMapping public WebResponse<List<EvaluationEvaluator>> list(@RequestParam(required = false) String creatorUserId) { return WebResponse.OK(service.lambdaQuery().eq(EvaluationEvaluator::getArchived, false).in(dataScopeService != null, EvaluationEvaluator::getCreatedBy, dataScopeService == null ? Collections.emptyList() : dataScopeService.readableCreatorIds(creatorUserId)).orderByDesc(EvaluationEvaluator::getUpdatedAt).list()); }
    @GetMapping("/{id}/versions") public WebResponse<List<EvaluationEvaluatorVersion>> versions(@PathVariable String id) { required(id); return WebResponse.OK(versionService.lambdaQuery().eq(EvaluationEvaluatorVersion::getEvaluatorId, id).eq(EvaluationEvaluatorVersion::getDeleted, false).orderByDesc(EvaluationEvaluatorVersion::getVersionNo).list()); }
    @PostMapping @Permission(path="/evaluation/evaluators",type=Permission.Type.Write)
    public WebResponse<String> create(@RequestBody EvaluationEvaluator item) { if(item==null||blank(item.getName()))return WebResponse.Error(422,I18nUtils.getMessage("agent.evaluation.evaluator.name.required")); if(!validKind(item.getKind()))return WebResponse.Error(422,I18nUtils.getMessage("agent.evaluation.evaluator.kind.invalid")); if(!validConfig(item.getKind(),item.getDraftConfigJson()))return WebResponse.Error(422,I18nUtils.getMessage("agent.evaluation.evaluator.config.invalid")); item.setId(null);item.setRevision(0L);item.setArchived(false);service.save(item);return WebResponse.OK(null,item.getId()); }
    @PutMapping("/{id}") @Permission(path="/evaluation/evaluators",type=Permission.Type.Write)
    public WebResponse<?> update(@PathVariable String id,@RequestBody EvaluationEvaluator request){EvaluationEvaluator item=required(id);if(request!=null&&!blank(request.getName()))item.setName(request.getName());if(request!=null&&!blank(request.getDraftConfigJson())){if(!validConfig(item.getKind(),request.getDraftConfigJson()))return WebResponse.Error(422,I18nUtils.getMessage("agent.evaluation.evaluator.config.invalid"));item.setDraftConfigJson(request.getDraftConfigJson());}item.setRevision((item.getRevision()==null?0L:item.getRevision())+1);service.updateById(item);return WebResponse.OK(null);}
    @DeleteMapping("/{id}") @Permission(path="/evaluation/evaluators",type=Permission.Type.Write)
    public WebResponse<?> delete(@PathVariable String id){required(id);deletionService.deleteEvaluator(id);return WebResponse.OK(I18nUtils.getMessage("agent.evaluation.data.delete.success"));}
    @PostMapping("/{id}/versions") @Permission(path="/evaluation/evaluators",type=Permission.Type.Write) @Transactional(rollbackFor=Exception.class)
    public WebResponse<?> publish(@PathVariable String id){EvaluationEvaluator evaluator=required(id);if(blank(evaluator.getDraftConfigJson()))return WebResponse.Error(422,I18nUtils.getMessage("agent.evaluation.evaluator.config.required"));if(!validConfig(evaluator.getKind(),evaluator.getDraftConfigJson()))return WebResponse.Error(422,I18nUtils.getMessage("agent.evaluation.evaluator.config.invalid"));List<EvaluationEvaluatorVersion> versions=versionService.lambdaQuery().eq(EvaluationEvaluatorVersion::getEvaluatorId,id).orderByDesc(EvaluationEvaluatorVersion::getVersionNo).last("LIMIT 1").list();int n=versions.isEmpty()?1:versions.get(0).getVersionNo()+1;EvaluationEvaluatorVersion v=new EvaluationEvaluatorVersion();v.setEvaluatorId(id);v.setVersionNo(n);v.setConfigJson(evaluator.getDraftConfigJson());v.setContentHash(sha256(v.getConfigJson()));v.setPublishedAt(System.currentTimeMillis());versionService.save(v);evaluator.setRevision((evaluator.getRevision()==null?0L:evaluator.getRevision())+1);service.updateById(evaluator);return WebResponse.OK(n);}
    private EvaluationEvaluator required(String id){EvaluationEvaluator value=service.getById(id);if(value==null||Boolean.TRUE.equals(value.getDeleted()))throw new ServerException(404,I18nUtils.getMessage("agent.evaluation.evaluator.not-found"));if(dataScopeService!=null)dataScopeService.assertReadable(value.getCreatedBy());return value;}
    private boolean validKind(String kind){return "RULE".equals(kind)||"LLM".equals(kind);}
    private boolean validConfig(String kind,String value){try{com.alibaba.fastjson2.JSONObject config=com.alibaba.fastjson2.JSONObject.parseObject(value);if("RULE".equals(kind)){String operator=config==null?null:config.getString("operator");if("JSON_PATH_EQUALS".equals(operator))return RuleEvaluationGrader.isSupportedJsonPath(config.getString("jsonPath"));if("JSON_SCHEMA".equals(operator)){Object schema=config.get("schema");return RuleEvaluationGrader.isValidJsonSchema(schema instanceof String?(String)schema:com.alibaba.fastjson2.JSON.toJSONString(schema));}return "EQUALS".equals(operator)||"CONTAINS".equals(operator)||"REGEX".equals(operator);}return "LLM".equals(kind)&&config!=null&&!blank(config.getString("modelId"))&&!blank(config.getString("rubric"));}catch(RuntimeException e){return false;}}
    private boolean blank(String s){return s==null||s.trim().isEmpty();}
    private String sha256(String value){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format("%02x",x));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
}
