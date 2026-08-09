package com.aether.agent.skill.service;

import com.aether.agent.dto.AgentChatDto;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMcpServer;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.service.AgentMcpServerService;
import com.aether.agent.skill.entity.AgentDefinitionSkillBinding;
import com.aether.agent.skill.entity.AgentSkill;
import com.aether.agent.skill.entity.AgentSkillExecutionConfig;
import com.aether.agent.skill.entity.AgentSkillKnowledgeBinding;
import com.aether.agent.skill.entity.AgentSkillResource;
import com.aether.agent.skill.entity.AgentSkillToolBinding;
import com.aether.agent.skill.entity.AgentSkillVersion;
import com.aether.agent.skill.service.impl.AgentSkillExecutionConfigServiceImpl;
import com.aether.agent.skill.service.impl.AgentSkillKnowledgeBindingServiceImpl;
import com.aether.agent.skill.service.impl.AgentSkillResourceServiceImpl;
import com.aether.agent.skill.service.impl.AgentSkillToolBindingServiceImpl;
import com.aether.agent.skill.service.impl.AgentSkillVersionServiceImpl;
import com.aether.agent.tools.AgentToolCatalog;
import com.aether.exception.ServerException;
import com.aether.storage.service.ObjectStorageService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Resolves an immutable, least-privilege Skill context before every model call. */
@Service
public class SkillContextService {
    private static final int MAX_MARKDOWN_CHARS = 12_000;
    private static final int MAX_TEMPLATE_CHARS = 2_000;

    private final AgentSkillService skillService;
    private final AgentSkillVersionServiceImpl versionService;
    private final AgentSkillToolBindingServiceImpl toolBindingService;
    private final AgentSkillKnowledgeBindingServiceImpl knowledgeBindingService;
    private final AgentSkillExecutionConfigServiceImpl executionConfigService;
    private final AgentSkillResourceServiceImpl resourceService;
    private final AgentToolCatalog toolCatalog;
    private final AgentMcpServerService mcpServerService;
    private final ObjectStorageService objectStorageService;
    private final String resourceBucket;
    private final SkillRouterService skillRouterService;

    public SkillContextService(AgentSkillService skillService, AgentSkillVersionServiceImpl versionService,
                               AgentSkillToolBindingServiceImpl toolBindingService, AgentSkillKnowledgeBindingServiceImpl knowledgeBindingService,
                               AgentSkillExecutionConfigServiceImpl executionConfigService, AgentSkillResourceServiceImpl resourceService,
                               AgentToolCatalog toolCatalog, AgentMcpServerService mcpServerService,
                               ObjectStorageService objectStorageService,
                               @Value("${skill.storage.bucket:${MINIO_SKILL_BUCKET:aether-skill}}") String resourceBucket,
                               SkillRouterService skillRouterService) {
        this.skillService = skillService; this.versionService = versionService; this.toolBindingService = toolBindingService;
        this.knowledgeBindingService = knowledgeBindingService; this.executionConfigService = executionConfigService;
        this.resourceService = resourceService; this.toolCatalog = toolCatalog; this.mcpServerService = mcpServerService;
        this.objectStorageService = objectStorageService; this.resourceBucket = resourceBucket;
        this.skillRouterService = skillRouterService;
    }

    public SkillRuntimeContext resolve(AgentDefinition agent, AgentChatDto dto) {
        return resolve(agent, dto, dto == null ? null : dto.getMessage(), null);
    }

    public SkillRuntimeContext resolve(AgentDefinition agent, AgentChatDto dto, String routingQuery, ModelProvider provider) {
        List<AgentDefinitionSkillBinding> installations = skillService.listBindings(agent.getId()).stream()
                .filter(item -> Integer.valueOf(1).equals(item.getStatus()))
                .sorted((left, right) -> Integer.compare(left.getPriority() == null ? 0 : left.getPriority(), right.getPriority() == null ? 0 : right.getPriority()))
                .collect(Collectors.toList());
        SkillRuntimeContext context = new SkillRuntimeContext();
        if (installations.isEmpty()) {
            context.setSystemPrompt(StringUtils.defaultString(agent.getSystemPrompt()));
            context.setTools(toolCatalog.getBoundTools(agent.getId()));
            context.setKnowledgeBaseIds(null);
            context.setSnapshot("{\"installed\":false}");
            return context;
        }
        SkillRouteDecision route = skillRouterService == null ? new SkillRouteDecision() : skillRouterService.route(agent, provider, routingQuery, installations);
        if (!route.isMatched()) {
            if (dto != null && dto.getSkillInputs() != null && !dto.getSkillInputs().isEmpty()) throw new ServerException(422, "No Skill is active for supplied skill inputs");
            context.setSystemPrompt(StringUtils.defaultString(agent.getSystemPrompt())); context.setTools(toolCatalog.getBoundTools(agent.getId())); context.setKnowledgeBaseIds(null);
            context.setSnapshot(JSON.toJSONString(java.util.Collections.<String, Object>singletonMap("routing", route))); return context;
        }
        installations = installations.stream().filter(item -> route.getSkillVersionId().equals(item.getSkillVersionId())).collect(Collectors.toList());

        Map<String, Map<String, Object>> inputs = dto == null || dto.getSkillInputs() == null ? Collections.<String, Map<String, Object>>emptyMap() : dto.getSkillInputs();
        List<AgentTool> boundTools = toolCatalog.getBoundTools(agent.getId());
        Set<String> boundToolIds = boundTools.stream().map(AgentTool::getId).collect(Collectors.toSet());
        Set<String> declaredToolIds = null, declaredKnowledgeBaseIds = null;
        StringBuilder prompt = new StringBuilder(StringUtils.defaultString(agent.getSystemPrompt()));
        List<Map<String, Object>> snapshotSkills = new ArrayList<>();
        Set<String> installedCodes = new LinkedHashSet<>(), artifactSkillCodes = new LinkedHashSet<>();

        for (AgentDefinitionSkillBinding installation : installations) {
            AgentSkill skill = skillService.getById(installation.getSkillId());
            AgentSkillVersion version = versionService.getById(installation.getSkillVersionId());
            if (skill == null || version == null || !Integer.valueOf(1).equals(skill.getStatus()) || !Integer.valueOf(1).equals(version.getStatus())) throw new ServerException(422, "Installed Skill version is unavailable");
            installedCodes.add(skill.getCode());
            AgentSkillExecutionConfig execution = executionConfigService.getOne(Wrappers.lambdaQuery(AgentSkillExecutionConfig.class).eq(AgentSkillExecutionConfig::getSkillVersionId, version.getId()));
            if (execution != null && Boolean.TRUE.equals(execution.getEnabled())) artifactSkillCodes.add(skill.getCode());

            List<AgentSkillToolBinding> declarations = toolBindingService.list(Wrappers.lambdaQuery(AgentSkillToolBinding.class).eq(AgentSkillToolBinding::getSkillVersionId, version.getId()));
            Set<String> toolIds = declarations.stream().map(AgentSkillToolBinding::getToolId).collect(Collectors.toCollection(LinkedHashSet::new));
            for (AgentSkillToolBinding declaration : declarations) {
                AgentTool tool = boundTools.stream().filter(item -> declaration.getToolId().equals(item.getId())).findFirst().orElse(null);
                if (Boolean.TRUE.equals(declaration.getRequired()) && (!boundToolIds.contains(declaration.getToolId()) || !isLiveMcpTool(tool))) throw new ServerException(422, "Required Skill tool is not available: " + declaration.getToolId());
            }
            declaredToolIds = merge(declaredToolIds, toolIds);
            Set<String> knowledgeIds = knowledgeBindingService.list(Wrappers.lambdaQuery(AgentSkillKnowledgeBinding.class).eq(AgentSkillKnowledgeBinding::getSkillVersionId, version.getId()))
                    .stream().map(AgentSkillKnowledgeBinding::getKnowledgeBaseId).collect(Collectors.toCollection(LinkedHashSet::new));
            declaredKnowledgeBaseIds = merge(declaredKnowledgeBaseIds, knowledgeIds);

            Map<String, Object> maskedInput = validateAndMaskInput(version.getInputSchema(), inputs.get(skill.getCode()));
            List<AgentSkillResource> resources = resourceService.list(Wrappers.lambdaQuery(AgentSkillResource.class).eq(AgentSkillResource::getSkillVersionId, version.getId()));
            prompt.append("\n\n[Installed Skill]\n## ").append(skill.getName()).append(" v").append(version.getVersionNo())
                    .append("\nPurpose: ").append(StringUtils.defaultString(skill.getDescription()))
                    .append("\nInstructions:\n").append(StringUtils.defaultString(version.getInstruction()));
            if (StringUtils.isNotBlank(version.getOutputSchema())) prompt.append("\nOutput contract (JSON Schema):\n").append(version.getOutputSchema());
            prompt.append(resolveResources(resources));
            if (!maskedInput.isEmpty()) prompt.append("\nValidated inputs: ").append(JSON.toJSONString(maskedInput));
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("skillId", skill.getId()); snapshot.put("code", skill.getCode()); snapshot.put("versionId", version.getId()); snapshot.put("versionNo", version.getVersionNo());
            snapshot.put("input", maskedInput); snapshot.put("resources", resources.stream().map(this::resourceSnapshot).collect(Collectors.toList()));
            snapshotSkills.add(snapshot);
        }
        for (String code : inputs.keySet()) if (!installedCodes.contains(code)) throw new ServerException(422, "Skill input is not installed on this Agent");
        Set<String> finalToolIds = declaredToolIds;
        List<AgentTool> tools = boundTools.stream().filter(item -> finalToolIds != null && finalToolIds.contains(item.getId()) && isLiveMcpTool(item)).collect(Collectors.toList());
        if (!artifactSkillCodes.isEmpty()) prompt.append("\n\n[Artifact Generation]\nWhen calling generate_artifact, skill_code must be exactly one of: ").append(String.join(", ", artifactSkillCodes)).append(".");
        prompt.append("\n\n[Platform Constraints]\n工具审批、安全与审计由平台统一控制。引用知识库资料时标注编号。");
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("installed", true); snapshot.put("routing", route); snapshot.put("skills", snapshotSkills); snapshot.put("toolIds", tools.stream().map(AgentTool::getId).collect(Collectors.toList()));
        snapshot.put("knowledgeBaseIds", declaredKnowledgeBaseIds == null ? Collections.emptySet() : declaredKnowledgeBaseIds); snapshot.put("artifactSkillCodes", artifactSkillCodes);
        context.setInstalled(true); context.setSystemPrompt(prompt.toString()); context.setTools(tools); context.setKnowledgeBaseIds(declaredKnowledgeBaseIds == null ? Collections.<String>emptySet() : declaredKnowledgeBaseIds); context.setArtifactSkillCodes(artifactSkillCodes); context.setSnapshot(JSON.toJSONString(snapshot));
        return context;
    }

    private Set<String> merge(Set<String> current, Set<String> next) { if (current == null) return new LinkedHashSet<>(next); current.addAll(next); return current; }

    private boolean isLiveMcpTool(AgentTool tool) {
        if (tool == null || !Integer.valueOf(1).equals(tool.getStatus()) || Boolean.TRUE.equals(tool.getDeleted()) || StringUtils.isBlank(tool.getMcpToolName()) || StringUtils.isBlank(tool.getMcpServerId())) return false;
        AgentMcpServer server = mcpServerService.getById(tool.getMcpServerId());
        return server != null && !Boolean.TRUE.equals(server.getDeleted()) && Integer.valueOf(1).equals(server.getStatus());
    }

    private Map<String, Object> resourceSnapshot(AgentSkillResource resource) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("id", resource.getId()); result.put("name", resource.getName()); result.put("type", resource.getType()); result.put("objectKey", resource.getObjectKey()); result.put("sha256", resource.getContentSha256()); result.put("size", resource.getSize()); return result;
    }

    private String resolveResources(List<AgentSkillResource> resources) {
        if (resources == null || resources.isEmpty()) return "";
        StringBuilder result = new StringBuilder("\n\n## 资源参考");
        for (AgentSkillResource resource : resources) {
            if (!Integer.valueOf(1).equals(resource.getStatus())) throw new ServerException(422, "Frozen Skill resource is disabled: " + resource.getName());
            byte[] content;
            try { content = objectStorageService.getObject(resourceBucket, resource.getObjectKey()); } catch (Exception e) { throw new ServerException(422, "Frozen Skill resource is unavailable: " + resource.getName()); }
            if (content == null || !sha256(content).equalsIgnoreCase(resource.getContentSha256())) throw new ServerException(422, "Frozen Skill resource checksum mismatch: " + resource.getName());
            result.append("\n- ").append(resource.getName()).append(" (").append(resource.getType()).append(")");
            if (StringUtils.isNotBlank(resource.getPurpose())) result.append("：").append(resource.getPurpose());
            if ("MARKDOWN".equals(resource.getType())) result.append("\n").append(toPlainText(content, MAX_MARKDOWN_CHARS));
            else if ("TEMPLATE".equals(resource.getType())) result.append("\nTemplate summary: ").append(toPlainText(content, MAX_TEMPLATE_CHARS));
            else result.append("\nScript reference: language=").append(resource.getLanguage()).append(", sha256=").append(resource.getContentSha256());
        }
        return result.toString();
    }

    private Map<String, Object> validateAndMaskInput(String schemaText, Map<String, Object> input) {
        Map<String, Object> value = input == null ? Collections.<String, Object>emptyMap() : input;
        if (StringUtils.isNotBlank(schemaText)) {
            JSONObject schema;
            try { schema = JSON.parseObject(schemaText); } catch (Exception e) { throw new ServerException(422, "Published Skill input schema is invalid"); }
            if (schema == null || (!StringUtils.isBlank(schema.getString("type")) && !"object".equals(schema.getString("type")))) throw new ServerException(422, "Published Skill input schema must be an object");
            List<String> required = schema.getList("required", String.class); if (required == null) required = Collections.emptyList();
            for (String name : required) if (!value.containsKey(name) || value.get(name) == null) throw new ServerException(422, "Skill input is missing required field: " + name);
            JSONObject properties = schema.getJSONObject("properties"); boolean additional = !Boolean.FALSE.equals(schema.getBoolean("additionalProperties"));
            for (Map.Entry<String, Object> item : value.entrySet()) {
                JSONObject property = properties == null ? null : properties.getJSONObject(item.getKey());
                if (property == null) { if (!additional) throw new ServerException(422, "Skill input has undeclared field: " + item.getKey()); continue; }
                String type = property.getString("type"); Object actual = item.getValue();
                if (("string".equals(type) && !(actual instanceof String)) || ("boolean".equals(type) && !(actual instanceof Boolean)) || ("number".equals(type) && !(actual instanceof Number)) || ("integer".equals(type) && (!(actual instanceof Number) || ((Number) actual).doubleValue() % 1 != 0)) || ("array".equals(type) && !(actual instanceof Collection)) || ("object".equals(type) && !(actual instanceof Map))) throw new ServerException(422, "Skill input field has invalid type: " + item.getKey());
            }
        }
        return mask(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mask(Map<String, Object> source) { Map<String, Object> target = new LinkedHashMap<>(); for (Map.Entry<String, Object> item : source.entrySet()) target.put(item.getKey(), isSensitive(item.getKey()) ? "***" : maskValue(item.getValue())); return target; }
    private Object maskValue(Object value) { if (value instanceof Map) return mask((Map<String, Object>) value); if (value instanceof Collection) { List<Object> result = new ArrayList<>(); for (Object item : (Collection<?>) value) result.add(maskValue(item)); return result; } return value; }
    private boolean isSensitive(String name) { String lower = StringUtils.defaultString(name).toLowerCase(); return lower.contains("password") || lower.contains("secret") || lower.contains("token") || lower.contains("credential") || lower.contains("authorization") || lower.endsWith("key"); }
    private String toPlainText(byte[] content, int maxChars) { String text = new String(content, StandardCharsets.UTF_8).replaceAll("(?s)<[^>]+>", " ").replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "").replaceAll("[`*_>#]", " ").replaceAll("\\s+", " ").trim(); return text.length() > maxChars ? text.substring(0, maxChars) + "…" : text; }
    private String sha256(byte[] content) { try { byte[] hash = MessageDigest.getInstance("SHA-256").digest(content); StringBuilder value = new StringBuilder(); for (byte item : hash) value.append(String.format("%02x", item)); return value.toString(); } catch (Exception e) { throw new ServerException(500, "Skill resource checksum failure"); } }
}
