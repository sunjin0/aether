package com.aether.agent.skill.service;

import com.aether.agent.dto.AgentChatDto;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMcpServer;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.service.AgentMcpServerService;
import com.aether.agent.service.CapabilityIndexService;
import com.aether.agent.skill.entity.AgentDefinitionSkillBinding;
import com.aether.agent.skill.entity.AgentSkill;
import com.aether.agent.skill.entity.AgentSkillKnowledgeBinding;
import com.aether.agent.skill.entity.AgentSkillResource;
import com.aether.agent.skill.entity.AgentSkillToolBinding;
import com.aether.agent.skill.entity.AgentSkillVersion;
import com.aether.agent.skill.service.impl.AgentSkillKnowledgeBindingServiceImpl;
import com.aether.agent.skill.service.impl.AgentSkillResourceServiceImpl;
import com.aether.agent.skill.service.impl.AgentSkillToolBindingServiceImpl;
import com.aether.agent.skill.service.impl.AgentSkillVersionServiceImpl;
import com.aether.agent.tools.AgentToolCatalog;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Resolves an immutable, least-privilege Skill context before every model call. */
@Service
public class SkillContextService {
    private static final int MAX_MARKDOWN_CHARS = 12_000;
    private static final int MAX_TEMPLATE_CHARS = 2_000;
    private static final int STATIC_PROMPT_CACHE_MAX_SIZE = 256;
    private final ConcurrentHashMap<String, String> staticPromptCache = new ConcurrentHashMap<>();

    private final AgentSkillService skillService;
    private final AgentSkillVersionServiceImpl versionService;
    private final AgentSkillToolBindingServiceImpl toolBindingService;
    private final AgentSkillKnowledgeBindingServiceImpl knowledgeBindingService;
    private final AgentSkillResourceServiceImpl resourceService;
    private final AgentToolCatalog toolCatalog;
    private final AgentMcpServerService mcpServerService;
    private final ObjectStorageService objectStorageService;
    private final String resourceBucket;
    private final SkillRouterService skillRouterService;
    private final CapabilityIndexService capabilityIndexService;

    /**
 * 创建 {@code SkillContextService} 实例。
 */
public SkillContextService(AgentSkillService skillService, AgentSkillVersionServiceImpl versionService,
                               AgentSkillToolBindingServiceImpl toolBindingService, AgentSkillKnowledgeBindingServiceImpl knowledgeBindingService,
                               AgentSkillResourceServiceImpl resourceService,
                               AgentToolCatalog toolCatalog, AgentMcpServerService mcpServerService,
                               ObjectStorageService objectStorageService,
                               @Value("${skill.storage.bucket:${MINIO_SKILL_BUCKET:aether-skill}}") String resourceBucket,
                               SkillRouterService skillRouterService, CapabilityIndexService capabilityIndexService) {
        this.skillService = skillService; this.versionService = versionService; this.toolBindingService = toolBindingService;
        this.knowledgeBindingService = knowledgeBindingService;
        this.resourceService = resourceService; this.toolCatalog = toolCatalog; this.mcpServerService = mcpServerService;
        this.objectStorageService = objectStorageService; this.resourceBucket = resourceBucket;
        this.skillRouterService = skillRouterService;
        this.capabilityIndexService = capabilityIndexService;
    }

    /**
 * 解析当前请求。
 */
public SkillRuntimeContext resolve(AgentDefinition agent, AgentChatDto dto) {
        return resolve(agent, dto, dto == null ? null : dto.getMessage(), null);
    }

    /**
 * 解析当前请求。
 */
public SkillRuntimeContext resolve(AgentDefinition agent, AgentChatDto dto, String routingQuery, ModelProvider provider) {
        List<AgentDefinitionSkillBinding> installations = skillService.listBindings(agent.getId()).stream()
                .filter(item -> Integer.valueOf(1).equals(item.getStatus()))
                .sorted((left, right) -> Integer.compare(left.getPriority() == null ? 0 : left.getPriority(), right.getPriority() == null ? 0 : right.getPriority()))
                .collect(Collectors.toList());
        SkillRuntimeContext context = new SkillRuntimeContext();
        List<AgentDefinitionSkillBinding> allInstallations = installations;
        if (installations.isEmpty()) {
            context.setSystemPrompt(withCapabilityIndex(agent, installations));
            List<AgentTool> boundTools = toolCatalog.getBoundTools(agent.getId());
            context.setTools(boundTools);
            context.setKnowledgeBaseIds(null);
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("installed", false);
            snapshot.put("toolIds", boundTools.stream().map(AgentTool::getId).collect(Collectors.toList()));
            context.setSnapshot(JSON.toJSONString(snapshot));
            return context;
        }
        SkillRouteDecision route = skillRouterService == null ? new SkillRouteDecision() : skillRouterService.route(agent, provider, routingQuery, installations);
        if (!route.isMatched()) {
            if (dto != null && dto.getSkillInputs() != null && !dto.getSkillInputs().isEmpty()) throw new ServerException(422, I18nUtils.getMessage("skill.context.no-active-skill"));
            List<AgentTool> boundTools = toolCatalog.getBoundTools(agent.getId());
            context.setSystemPrompt(withCapabilityIndex(agent, allInstallations)); context.setTools(boundTools); context.setKnowledgeBaseIds(null);
            Map<String, Object> snapshot = new LinkedHashMap<>(); snapshot.put("routing", route); snapshot.put("toolIds", boundTools.stream().map(AgentTool::getId).collect(Collectors.toList()));
            context.setSnapshot(JSON.toJSONString(snapshot)); return context;
        }
        installations = installations.stream().filter(item -> route.getSkillVersionId().equals(item.getSkillVersionId())).collect(Collectors.toList());

        Map<String, Map<String, Object>> inputs = dto == null || dto.getSkillInputs() == null ? Collections.<String, Map<String, Object>>emptyMap() : dto.getSkillInputs();
        List<AgentTool> boundTools = toolCatalog.getBoundTools(agent.getId());
        Set<String> boundToolIds = boundTools.stream().map(AgentTool::getId).collect(Collectors.toSet());
        Set<String> declaredToolIds = null, declaredKnowledgeBaseIds = null;
        Set<String> requiredToolIds = new LinkedHashSet<>();
        StringBuilder prompt = new StringBuilder(StringUtils.defaultString(agent.getSystemPrompt()));
        List<Map<String, Object>> snapshotSkills = new ArrayList<>();
        Set<String> installedCodes = new LinkedHashSet<>();

        for (AgentDefinitionSkillBinding installation : installations) {
            AgentSkill skill = skillService.getById(installation.getSkillId());
            AgentSkillVersion version = versionService.getById(installation.getSkillVersionId());
            if (skill == null || version == null || !Integer.valueOf(1).equals(skill.getStatus()) || !Integer.valueOf(1).equals(version.getStatus())) throw new ServerException(422, I18nUtils.getMessage("skill.context.version.unavailable"));
            installedCodes.add(skill.getCode());
            List<AgentSkillToolBinding> declarations = toolBindingService.list(Wrappers.lambdaQuery(AgentSkillToolBinding.class).eq(AgentSkillToolBinding::getSkillVersionId, version.getId()));
            Set<String> toolIds = declarations.stream().map(AgentSkillToolBinding::getToolId).collect(Collectors.toCollection(LinkedHashSet::new));
            for (AgentSkillToolBinding declaration : declarations) {
                AgentTool tool = boundTools.stream().filter(item -> declaration.getToolId().equals(item.getId())).findFirst().orElse(null);
                if (Boolean.TRUE.equals(declaration.getRequired()) && (!boundToolIds.contains(declaration.getToolId()) || !isLiveMcpTool(tool))) throw new ServerException(422, I18nUtils.getMessage("skill.context.required-tool.unavailable", new Object[]{declaration.getToolId()}));
                if (Boolean.TRUE.equals(declaration.getRequired())) requiredToolIds.add(declaration.getToolId());
            }
            declaredToolIds = merge(declaredToolIds, toolIds);
            Set<String> knowledgeIds = knowledgeBindingService.list(Wrappers.lambdaQuery(AgentSkillKnowledgeBinding.class).eq(AgentSkillKnowledgeBinding::getSkillVersionId, version.getId()))
                    .stream().map(AgentSkillKnowledgeBinding::getKnowledgeBaseId).collect(Collectors.toCollection(LinkedHashSet::new));
            declaredKnowledgeBaseIds = merge(declaredKnowledgeBaseIds, knowledgeIds);

            Map<String, Object> maskedInput = validateAndMaskInput(version.getInputSchema(), inputs.get(skill.getCode()));
            List<AgentSkillResource> resources = resourceService.list(Wrappers.lambdaQuery(AgentSkillResource.class).eq(AgentSkillResource::getSkillVersionId, version.getId()));
            prompt.append(resolveStaticPrompt(skill, version, resources));
            if (!maskedInput.isEmpty()) prompt.append("\nValidated inputs: ").append(JSON.toJSONString(maskedInput));
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("skillId", skill.getId()); snapshot.put("code", skill.getCode()); snapshot.put("versionId", version.getId()); snapshot.put("versionNo", version.getVersionNo());
            snapshot.put("input", maskedInput); snapshot.put("resources", resources.stream().map(this::resourceSnapshot).collect(Collectors.toList()));
            snapshotSkills.add(snapshot);
        }
        for (String code : inputs.keySet()) if (!installedCodes.contains(code)) throw new ServerException(422, I18nUtils.getMessage("skill.context.input.not-installed"));
        // Agent 工具和知识库绑定定义运行期的授权边界。Skill 声明只约束 Skill 自己的
        // 模板、资源及 required 工具，不得收窄 Agent 已绑定的工具或知识库范围。
        List<AgentTool> tools = boundTools.stream().filter(this::isLiveMcpTool).collect(Collectors.toList());
        if (tools.stream().anyMatch(tool -> "generate_artifact".equals(tool.getMcpToolName()))) {
            prompt.append("\n\n[Artifact Generation]\nUse generate_artifact for file output. Provide title, content and format only; never select a Skill, script or template. This Skill's instructions above are the applicable document specification.");
        }
        prompt.append("\n\n[Platform Constraints]\n工具审批、安全与审计由平台统一控制。知识库引用规则由检索上下文统一提供。");
        prompt.append(capabilityIndexService.buildIndex(agent.getId(), allInstallations));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("installed", true); snapshot.put("routing", route); snapshot.put("skills", snapshotSkills); snapshot.put("toolIds", tools.stream().map(AgentTool::getId).collect(Collectors.toList()));
        snapshot.put("skillKnowledgeBaseIds", declaredKnowledgeBaseIds == null ? Collections.emptySet() : declaredKnowledgeBaseIds);
        snapshot.put("knowledgeBaseScope", "agent-bound");
        context.setInstalled(true); context.setSystemPrompt(prompt.toString()); context.setTools(tools);
        // null 表示检索服务使用全部启用的 Agent 知识库绑定；空集合才表示显式禁止检索。
        context.setKnowledgeBaseIds(null); context.setRequiredToolIds(requiredToolIds); context.setSnapshot(JSON.toJSONString(snapshot));
        return context;
    }

    /**
 * 处理merge。
 */
private Set<String> merge(Set<String> current, Set<String> next) { if (current == null) return new LinkedHashSet<>(next); current.addAll(next); return current; }

    /**
     * 在系统提示末尾追加能力索引（工具 + 全部已装 Skill）。
     */
    private String withCapabilityIndex(AgentDefinition agent, List<AgentDefinitionSkillBinding> installations) {
        String base = StringUtils.defaultString(agent.getSystemPrompt());
        return base + capabilityIndexService.buildIndex(agent.getId(), installations);
    }
    /** Caches immutable Skill version content; request-specific inputs are deliberately appended outside this cache. */
    private String resolveStaticPrompt(AgentSkill skill, AgentSkillVersion version, List<AgentSkillResource> resources) {
        String key = staticPromptCacheKey(skill, version, resources);
        String cached = staticPromptCache.get(key);
        if (cached != null) return cached;
        if (staticPromptCache.size() >= STATIC_PROMPT_CACHE_MAX_SIZE) staticPromptCache.clear();
        StringBuilder value = new StringBuilder("\n\n[Installed Skill]\n## ").append(skill.getName()).append(" v").append(version.getVersionNo())
                .append("\nPurpose: ").append(StringUtils.defaultString(skill.getDescription()))
                .append("\nInstructions:\n").append(StringUtils.defaultString(version.getInstruction()));
        if (hasMeaningfulOutputSchema(version.getOutputSchema())) {
            value.append("\nOutput mode: JSON. Return one valid JSON object that conforms to this schema; do not wrap it in Markdown fences.")
                    .append("\nStructured output contract (JSON Schema):\n").append(version.getOutputSchema());
        } else {
            value.append("\nOutput mode: Markdown. Use readable Markdown unless the user explicitly requests another format.");
        }
        value.append(resolveResources(resources));
        String built = value.toString();
        String existing = staticPromptCache.putIfAbsent(key, built);
        return existing == null ? built : existing;
    }

    /**
 * 处理staticPrompt缓存Key。
 */
private String staticPromptCacheKey(AgentSkill skill, AgentSkillVersion version, List<AgentSkillResource> resources) {
        StringBuilder value = new StringBuilder(skill.getId()).append('|').append(skill.getName()).append('|')
                .append(skill.getDescription()).append('|').append(version.getId()).append('|')
                .append(version.getVersionNo()).append('|').append(version.getInstruction()).append('|').append(version.getOutputSchema());
        List<AgentSkillResource> orderedResources = new ArrayList<>(resources);
        orderedResources.sort((left, right) -> StringUtils.defaultString(left.getId()).compareTo(StringUtils.defaultString(right.getId())));
        for (AgentSkillResource resource : orderedResources) {
            value.append('|').append(resource.getId()).append(':').append(resource.getStatus()).append(':')
                    .append(resource.getType()).append(':').append(resource.getContentSha256()).append(':')
                    .append(resource.getPurpose());
        }
        return sha256(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Do not distract the model with placeholder or unconstrained schemas. An output contract
     * is useful only when it actually restricts the response shape or values.
     */
    private boolean hasMeaningfulOutputSchema(String schemaText) {
        if (StringUtils.isBlank(schemaText)) return false;
        try {
            JSONObject schema = JSON.parseObject(schemaText);
            if (schema == null || schema.isEmpty()) return false;
            JSONObject properties = schema.getJSONObject("properties");
            return (properties != null && !properties.isEmpty())
                    || (schema.getList("required", String.class) != null && !schema.getList("required", String.class).isEmpty())
                    || Boolean.FALSE.equals(schema.getBoolean("additionalProperties"))
                    || schema.containsKey("enum") || schema.containsKey("oneOf")
                    || schema.containsKey("anyOf") || schema.containsKey("allOf");
        } catch (Exception ignored) {
            // Invalid schemas are managed by the Skill version publishing flow; never present
            // malformed JSON as an instruction to the model.
            return false;
        }
    }

    /**
 * 判断是否为LiveMcpTool。
 */
private boolean isLiveMcpTool(AgentTool tool) {
        if (tool == null || !Integer.valueOf(1).equals(tool.getStatus()) || Boolean.TRUE.equals(tool.getDeleted()) || StringUtils.isBlank(tool.getMcpToolName()) || StringUtils.isBlank(tool.getMcpServerId())) return false;
        AgentMcpServer server = mcpServerService.getById(tool.getMcpServerId());
        return server != null && !Boolean.TRUE.equals(server.getDeleted()) && Integer.valueOf(1).equals(server.getStatus());
    }

    /**
 * 资源Snapshot。
 */
private Map<String, Object> resourceSnapshot(AgentSkillResource resource) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("id", resource.getId()); result.put("name", resource.getName()); result.put("type", resource.getType()); result.put("objectKey", resource.getObjectKey()); result.put("sha256", resource.getContentSha256()); result.put("size", resource.getSize()); return result;
    }

    /**
 * 解析Resources。
 */
private String resolveResources(List<AgentSkillResource> resources) {
        if (resources == null || resources.isEmpty()) return "";
        StringBuilder result = new StringBuilder("\n\n## 资源参考");
        for (AgentSkillResource resource : resources) {
            // Artifact generation is now provided by the platform tool. Legacy script
            // resources are no longer executed or exposed to the model, so an orphaned
            // script object must not prevent the installed Skill from being used.
            if ("SCRIPT".equals(resource.getType())) {
                continue;
            }
            if (!Integer.valueOf(1).equals(resource.getStatus())) throw new ServerException(422, I18nUtils.getMessage("skill.context.resource.disabled", new Object[]{resource.getName()}));
            byte[] content;
            try { content = objectStorageService.getObject(resourceBucket, resource.getObjectKey()); } catch (Exception e) { throw new ServerException(422, I18nUtils.getMessage("skill.context.resource.unavailable", new Object[]{resource.getName()})); }
            if (content == null || !sha256(content).equalsIgnoreCase(resource.getContentSha256())) throw new ServerException(422, I18nUtils.getMessage("skill.context.resource.checksum-mismatch", new Object[]{resource.getName()}));
            result.append("\n- ").append(resource.getName()).append(" (").append(resource.getType()).append(")");
            if (StringUtils.isNotBlank(resource.getPurpose())) result.append("：").append(resource.getPurpose());
            if ("MARKDOWN".equals(resource.getType())) result.append("\n").append(toPlainText(content, MAX_MARKDOWN_CHARS));
            else if ("TEMPLATE".equals(resource.getType())) result.append("\nTemplate summary: ").append(toPlainText(content, MAX_TEMPLATE_CHARS));
            else result.append("\nScript reference: language=").append(resource.getLanguage()).append(", sha256=").append(resource.getContentSha256());
        }
        return result.toString();
    }

    /**
 * 校验AndMaskInput。
 */
private Map<String, Object> validateAndMaskInput(String schemaText, Map<String, Object> input) {
        Map<String, Object> value = input == null ? Collections.<String, Object>emptyMap() : input;
        if (StringUtils.isNotBlank(schemaText)) {
            JSONObject schema;
            try { schema = JSON.parseObject(schemaText); } catch (Exception e) { throw new ServerException(422, I18nUtils.getMessage("skill.context.input-schema.invalid")); }
            if (schema == null || (!StringUtils.isBlank(schema.getString("type")) && !"object".equals(schema.getString("type")))) throw new ServerException(422, I18nUtils.getMessage("skill.context.input-schema.object-required"));
            List<String> required = schema.getList("required", String.class); if (required == null) required = Collections.emptyList();
            for (String name : required) if (!value.containsKey(name) || value.get(name) == null) throw new ServerException(422, I18nUtils.getMessage("skill.context.input.required-field.missing", new Object[]{name}));
            JSONObject properties = schema.getJSONObject("properties"); boolean additional = !Boolean.FALSE.equals(schema.getBoolean("additionalProperties"));
            for (Map.Entry<String, Object> item : value.entrySet()) {
                JSONObject property = properties == null ? null : properties.getJSONObject(item.getKey());
                if (property == null) { if (!additional) throw new ServerException(422, I18nUtils.getMessage("skill.context.input.field.undeclared", new Object[]{item.getKey()})); continue; }
                String type = property.getString("type"); Object actual = item.getValue();
                if (("string".equals(type) && !(actual instanceof String)) || ("boolean".equals(type) && !(actual instanceof Boolean)) || ("number".equals(type) && !(actual instanceof Number)) || ("integer".equals(type) && (!(actual instanceof Number) || ((Number) actual).doubleValue() % 1 != 0)) || ("array".equals(type) && !(actual instanceof Collection)) || ("object".equals(type) && !(actual instanceof Map))) throw new ServerException(422, I18nUtils.getMessage("skill.context.input.field.invalid-type", new Object[]{item.getKey()}));
            }
        }
        return mask(value);
    }

    /**
 * 处理mask。
 */
@SuppressWarnings("unchecked")
    private Map<String, Object> mask(Map<String, Object> source) { Map<String, Object> target = new LinkedHashMap<>(); for (Map.Entry<String, Object> item : source.entrySet()) target.put(item.getKey(), isSensitive(item.getKey()) ? "***" : maskValue(item.getValue())); return target; }
    /**
 * 处理maskValue。
 */
private Object maskValue(Object value) { if (value instanceof Map) return mask((Map<String, Object>) value); if (value instanceof Collection) { List<Object> result = new ArrayList<>(); for (Object item : (Collection<?>) value) result.add(maskValue(item)); return result; } return value; }
    /**
 * 判断是否为Sensitive。
 */
private boolean isSensitive(String name) { String lower = StringUtils.defaultString(name).toLowerCase(); return lower.contains("password") || lower.contains("secret") || lower.contains("token") || lower.contains("credential") || lower.contains("authorization") || lower.endsWith("key"); }
    /**
 * 处理toPlainText。
 */
private String toPlainText(byte[] content, int maxChars) { String text = new String(content, StandardCharsets.UTF_8).replaceAll("(?s)<[^>]+>", " ").replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "").replaceAll("[`*_>#]", " ").replaceAll("\\s+", " ").trim(); return text.length() > maxChars ? text.substring(0, maxChars) + "…" : text; }
    /**
 * 处理sha256。
 */
private String sha256(byte[] content) { try { byte[] hash = MessageDigest.getInstance("SHA-256").digest(content); StringBuilder value = new StringBuilder(); for (byte item : hash) value.append(String.format("%02x", item)); return value.toString(); } catch (Exception e) { throw new ServerException(500, I18nUtils.getMessage("skill.context.resource.checksum.failure")); } }
}
