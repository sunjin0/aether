package com.aether.evaluation.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.tools.AgentToolCatalog;
import com.aether.agent.service.AgentKnowledgeBaseBindingService;
import com.aether.agent.entity.AgentKnowledgeBaseBinding;
import com.aether.agent.skill.service.AgentSkillService;
import com.aether.agent.skill.entity.AgentDefinitionSkillBinding;
import com.aether.agent.skill.entity.AgentSkillVersion;
import com.aether.agent.skill.service.impl.AgentSkillVersionServiceImpl;
import com.aether.evaluation.entity.EvaluationTargetSnapshot;
import com.aether.evaluation.mapper.EvaluationTargetSnapshotMapper;
import com.aether.evaluation.service.EvaluationSnapshotService;
import com.aether.i18n.I18nUtils;
import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.entity.AgentWorkflowVersion;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.workflow.service.AgentWorkflowVersionService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Creates immutable evaluation snapshots only from server-side definitions. */
@Service
public class EvaluationSnapshotServiceImpl extends ServiceImpl<EvaluationTargetSnapshotMapper, EvaluationTargetSnapshot> implements EvaluationSnapshotService {
    private final AgentDefinitionService agentDefinitionService;
    private final AgentWorkflowService workflowService;
    private final AgentWorkflowVersionService workflowVersionService;
    private final AgentToolCatalog toolCatalog;
    private final AgentSkillService skillService;
    private final AgentKnowledgeBaseBindingService knowledgeBindingService;
    @Autowired(required = false)
    private AgentSkillVersionServiceImpl skillVersionService;

    public EvaluationSnapshotServiceImpl(AgentDefinitionService agentDefinitionService, AgentWorkflowService workflowService, AgentWorkflowVersionService workflowVersionService, AgentToolCatalog toolCatalog, AgentSkillService skillService, AgentKnowledgeBaseBindingService knowledgeBindingService) {
        this.agentDefinitionService = agentDefinitionService;
        this.workflowService = workflowService;
        this.workflowVersionService = workflowVersionService;
        this.toolCatalog = toolCatalog;
        this.skillService=skillService; this.knowledgeBindingService=knowledgeBindingService;
    }

    @Override public EvaluationTargetSnapshot getById(String id) { return super.getById(id); }
    @Override public EvaluationTargetSnapshot create(String type, String target, String kind, String version, String json, String user) {
        if (json == null || json.trim().isEmpty()) throw new IllegalArgumentException(I18nUtils.getMessage("agent.evaluation.snapshot.content.invalid"));
        String canonical=canonicalJson(json);
        EvaluationTargetSnapshot snapshot = new EvaluationTargetSnapshot();
        snapshot.setTargetType(type); snapshot.setTargetId(target); snapshot.setSourceKind(kind); snapshot.setSourceVersionId(version);
        snapshot.setSnapshotJson(canonical); snapshot.setFingerprint(hash(canonical)); snapshot.setCreatedBy(user); save(snapshot); return snapshot;
    }
    @Override public EvaluationTargetSnapshot createCurrentSnapshot(String type, String target, String user) {
        if ("AGENT".equals(type)) return snapshotAgent(target, user);
        if ("WORKFLOW".equals(type)) return snapshotWorkflow(target, user);
        throw new IllegalArgumentException(I18nUtils.getMessage("agent.evaluation.target-type.invalid"));
    }
    private EvaluationTargetSnapshot snapshotAgent(String target, String user) {
        AgentDefinition agent = agentDefinitionService.getById(target);
        if (agent == null || Boolean.TRUE.equals(agent.getDeleted())) throw new IllegalArgumentException(I18nUtils.getMessage("agent.evaluation.target.not-found"));
        JSONObject json = new JSONObject();
        json.put("snapshotSchemaVersion", 2); json.put("systemPrompt", agent.getSystemPrompt()); json.put("modelProviderId", agent.getModelProviderId()); json.put("modelId", agent.getModelId()); json.put("contextCompressionModelId", agent.getContextCompressionModelId()); json.put("model", agent.getModel()); json.put("temperature", agent.getTemperature()); json.put("maxTokens", agent.getMaxTokens()); json.put("maxToolRounds", agent.getMaxToolRounds()); json.put("defaultThinking", agent.getDefaultThinking()); json.put("defaultReasoningEffort", agent.getDefaultReasoningEffort()); json.put("executionMode", agent.getExecutionMode());
        // AgentTool stores identifiers and schemas only; provider credentials remain runtime-resolved and are never serialized.
        json.put("tools", toolCatalog.getBoundTools(agent.getId()));
        // Keep dependency references deterministic and credential-free. Runtime content is deliberately not copied.
        List<Map<String,Object>> skills=new ArrayList<>();for(AgentDefinitionSkillBinding binding:skillService.listBindings(agent.getId()))if(binding.getStatus()==null||binding.getStatus()==1){AgentSkillVersion version=skillVersionService==null?null:skillVersionService.getById(binding.getSkillVersionId());if(version==null)throw new IllegalArgumentException(I18nUtils.getMessage("agent.evaluation.snapshot.dependency.unsupported"));Map<String,Object> value=new LinkedHashMap<>();value.put("skillId",binding.getSkillId());value.put("skillVersionId",binding.getSkillVersionId());value.put("priority",binding.getPriority());value.put("instruction",version.getInstruction());value.put("inputSchema",version.getInputSchema());value.put("outputSchema",version.getOutputSchema());value.put("toolPolicy",version.getToolPolicy());skills.add(value);}skills.sort(java.util.Comparator.comparing(value->String.valueOf(value.get("skillVersionId"))));json.put("skills",skills);
        List<Map<String,Object>> knowledgeBases=new ArrayList<>();for(AgentKnowledgeBaseBinding binding:knowledgeBindingService.lambdaQuery().eq(AgentKnowledgeBaseBinding::getAgentDefinitionId,agent.getId()).eq(AgentKnowledgeBaseBinding::getStatus,1).eq(AgentKnowledgeBaseBinding::getDeleted,false).list()){Map<String,Object> value=new LinkedHashMap<>();value.put("knowledgeBaseId",binding.getKnowledgeBaseId());knowledgeBases.add(value);}knowledgeBases.sort(java.util.Comparator.comparing(value->String.valueOf(value.get("knowledgeBaseId"))));json.put("knowledgeBases",knowledgeBases);
        return create("AGENT", target, "CURRENT", null, JSON.toJSONString(json), user);
    }
    private EvaluationTargetSnapshot snapshotWorkflow(String target, String user) {
        AgentWorkflow workflow = workflowService.getById(target);
        if (workflow == null || Boolean.TRUE.equals(workflow.getDeleted())) throw new IllegalArgumentException(I18nUtils.getMessage("agent.evaluation.target.not-found"));
        AgentWorkflowVersion published = workflow.getPublishedVersion() == null ? null : workflowVersionService.getOne(Wrappers.lambdaQuery(AgentWorkflowVersion.class).eq(AgentWorkflowVersion::getWorkflowId, target).eq(AgentWorkflowVersion::getVersionNo, workflow.getPublishedVersion()).eq(AgentWorkflowVersion::getDeleted, false));
        JSONObject json = new JSONObject();
        json.put("snapshotSchemaVersion", 1); json.put("nodes", published == null ? workflow.getNodes() : published.getNodes()); json.put("edges", published == null ? workflow.getEdges() : published.getEdges()); json.put("inputSchema", published == null ? workflow.getInputSchema() : published.getInputSchema()); json.put("outputSchema", published == null ? workflow.getOutputSchema() : published.getOutputSchema());
        return create("WORKFLOW", target, published == null ? "DRAFT" : "PUBLISHED", published == null ? null : published.getId(), JSON.toJSONString(json), user);
    }
    private String hash(String value) { try { byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder hex = new StringBuilder(); for (byte item : digest) hex.append(String.format("%02x", item)); return hex.toString(); } catch (Exception exception) { throw new IllegalStateException(exception); } }
    private String canonicalJson(String value){try{return JSON.toJSONString(canonicalize(JSON.parse(value)));}catch(RuntimeException ex){throw new IllegalArgumentException(I18nUtils.getMessage("agent.evaluation.snapshot.content.invalid"),ex);}}
    @SuppressWarnings("unchecked") private Object canonicalize(Object value){if(value instanceof JSONObject object){Map<String,Object> ordered=new TreeMap<>();for(Map.Entry<String,Object> entry:object.entrySet())ordered.put(entry.getKey(),canonicalize(entry.getValue()));return ordered;}if(value instanceof com.alibaba.fastjson2.JSONArray array){List<Object> out=new ArrayList<>();for(Object item:array)out.add(canonicalize(item));return out;}if(value instanceof Map<?,?> map){Map<String,Object> ordered=new TreeMap<>();for(Map.Entry<?,?> entry:map.entrySet())ordered.put(String.valueOf(entry.getKey()),canonicalize(entry.getValue()));return ordered;}return value;}
}
