package com.aether.agent.skill.service.impl;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.AgentToolService;
import com.aether.agent.skill.dto.AgentSkillBindingUpdateDto;
import com.aether.agent.skill.dto.AgentSkillDraftDto;
import com.aether.agent.skill.dto.AgentSkillInstallDto;
import com.aether.agent.skill.dto.AgentSkillPreviewDto;
import com.aether.agent.skill.dto.AgentSkillToolDto;
import com.aether.agent.skill.entity.*;
import com.aether.agent.skill.mapper.AgentSkillMapper;
import com.aether.agent.skill.service.AgentSkillService;
import com.aether.agent.skill.service.SkillRoutingIndexService;
import com.aether.agent.skill.vo.AgentSkillDetailVo;
import com.aether.agent.skill.vo.AgentSkillPreviewVo;
import com.aether.agent.skill.vo.AgentSkillPublishCheckVo;
import com.aether.agent.skill.vo.AgentSkillVo;
import com.aether.agent.skill.vo.AgentSkillStatisticsVo;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.storage.service.ObjectStorageService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import com.alibaba.fastjson2.JSON;

/**
 * Skill 生命周期实现。
 * 草稿和发布版本的子项均由本服务在事务中维护，避免控制器绕过不可变性约束。
 */
@Service
public class AgentSkillServiceImpl extends ServiceImpl<AgentSkillMapper, AgentSkill> implements AgentSkillService {
    private static final int MAX_DRAFT_RESOURCE_COUNT = 20;
    private final AgentSkillVersionServiceImpl versionService;
    private final AgentSkillToolBindingServiceImpl toolBindingService;
    private final AgentSkillKnowledgeBindingServiceImpl knowledgeBindingService;
    private final AgentSkillResourceServiceImpl resourceService;
    private final AgentDefinitionSkillBindingServiceImpl definitionBindingService;
    private final AgentDefinitionService agentDefinitionService;
    private final AgentToolService agentToolService;
    private final ObjectStorageService objectStorageService;
    private final String resourceBucket;
    private final long maxResourceSize;
    private final SkillRoutingIndexService routingIndexService;

    public AgentSkillServiceImpl(AgentSkillVersionServiceImpl versionService,
                                 AgentSkillToolBindingServiceImpl toolBindingService,
                                 AgentSkillKnowledgeBindingServiceImpl knowledgeBindingService,
                                 AgentSkillResourceServiceImpl resourceService,
                                 AgentDefinitionSkillBindingServiceImpl definitionBindingService,
                                 AgentDefinitionService agentDefinitionService,
                                 AgentToolService agentToolService,
                                 ObjectStorageService objectStorageService,
                                 SkillRoutingIndexService routingIndexService,
                                 @Value("${skill.storage.bucket:${MINIO_SKILL_BUCKET:aether-skill}}") String resourceBucket,
                                 @Value("${skill.storage.max-size:10485760}") long maxResourceSize) {
        this.versionService = versionService;
        this.toolBindingService = toolBindingService;
        this.knowledgeBindingService = knowledgeBindingService;
        this.resourceService = resourceService;
        this.definitionBindingService = definitionBindingService;
        this.agentDefinitionService = agentDefinitionService;
        this.agentToolService = agentToolService;
        this.objectStorageService = objectStorageService;
        this.routingIndexService = routingIndexService;
        this.resourceBucket = resourceBucket;
        this.maxResourceSize = maxResourceSize;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createDraft(AgentSkillDraftDto dto) {
        validateIdentity(dto);
        if (lambdaQuery().eq(AgentSkill::getCode, dto.getCode()).one() != null) {
            throw new ServerException(409, I18nUtils.getMessage("skill.code.exists"));
        }
        AgentSkill skill = new AgentSkill();
        applyIdentity(skill, dto);
        skill.setStatus(0);
        save(skill);
        AgentSkillVersion draft = new AgentSkillVersion();
        draft.setSkillId(skill.getId());
        draft.setStatus(0);
        applyDraft(draft, dto);
        versionService.save(draft);
        replaceDraftBindings(draft.getId(), dto);
        return skill.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createNextDraft(String skillId) {
        requireSkill(skillId);
        if (versionService.count(Wrappers.lambdaQuery(AgentSkillVersion.class).eq(AgentSkillVersion::getSkillId, skillId).eq(AgentSkillVersion::getStatus, 0)) > 0) {
            throw new ServerException(409, I18nUtils.getMessage("skill.draft.editable-exists"));
        }
        // 新草稿从最新发布版本复制，确保管理员修改不会影响已安装的生产版本。
        AgentSkillVersion source = versionService.getOne(Wrappers.lambdaQuery(AgentSkillVersion.class).eq(AgentSkillVersion::getSkillId, skillId).eq(AgentSkillVersion::getStatus, 1).orderByDesc(AgentSkillVersion::getVersionNo).last("limit 1"));
        if (source == null) throw new ServerException(409, I18nUtils.getMessage("skill.draft.published-source.required"));
        AgentSkillVersion draft = new AgentSkillVersion();
        draft.setSkillId(skillId); draft.setStatus(0); draft.setInstruction(source.getInstruction()); draft.setInputSchema(source.getInputSchema()); draft.setOutputSchema(source.getOutputSchema()); draft.setToolPolicy(source.getToolPolicy()); draft.setChangeNote(source.getChangeNote()); draft.setRoutingSummary(source.getRoutingSummary()); draft.setTriggerTerms(source.getTriggerTerms()); draft.setExcludeTerms(source.getExcludeTerms()); draft.setRoutingExamples(source.getRoutingExamples());
        versionService.save(draft);
        for (AgentSkillToolBinding sourceBinding : toolBindingService.list(Wrappers.lambdaQuery(AgentSkillToolBinding.class).eq(AgentSkillToolBinding::getSkillVersionId, source.getId()))) {
            AgentSkillToolBinding copy = new AgentSkillToolBinding(); copy.setSkillVersionId(draft.getId()); copy.setToolId(sourceBinding.getToolId()); copy.setRequired(sourceBinding.getRequired()); copy.setPriority(sourceBinding.getPriority()); toolBindingService.save(copy);
        }
        for (AgentSkillKnowledgeBinding sourceBinding : knowledgeBindingService.list(Wrappers.lambdaQuery(AgentSkillKnowledgeBinding.class).eq(AgentSkillKnowledgeBinding::getSkillVersionId, source.getId()))) {
            AgentSkillKnowledgeBinding copy = new AgentSkillKnowledgeBinding(); copy.setSkillVersionId(draft.getId()); copy.setKnowledgeBaseId(sourceBinding.getKnowledgeBaseId()); knowledgeBindingService.save(copy);
        }
        for (AgentSkillResource sourceResource : resourceService.list(Wrappers.lambdaQuery(AgentSkillResource.class).eq(AgentSkillResource::getSkillVersionId, source.getId()))) {
            AgentSkillResource copy = new AgentSkillResource(); copy.setSkillVersionId(draft.getId()); copy.setName(sourceResource.getName()); copy.setType(sourceResource.getType()); copy.setLanguage(sourceResource.getLanguage()); copy.setObjectKey(sourceResource.getObjectKey()); copy.setContentSha256(sourceResource.getContentSha256()); copy.setSize(sourceResource.getSize()); copy.setPurpose(sourceResource.getPurpose()); copy.setStatus(sourceResource.getStatus()); resourceService.save(copy);
        }
        return draft.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDraft(String skillId, AgentSkillDraftDto dto) {
        AgentSkill skill = requireSkill(skillId);
        validateIdentity(dto);
        AgentSkill sameCode = lambdaQuery().eq(AgentSkill::getCode, dto.getCode()).ne(AgentSkill::getId, skillId).one();
        if (sameCode != null) throw new ServerException(409, I18nUtils.getMessage("skill.code.exists"));
        applyIdentity(skill, dto);
        updateById(skill);
        AgentSkillVersion draft = requireDraft(skillId);
        applyDraft(draft, dto);
        versionService.updateById(draft);
        replaceDraftBindings(draft.getId(), dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentSkillVersion publish(String skillId, String userId) {
        AgentSkillPublishCheckVo check = publishCheck(skillId);
        if (!check.isReady()) throw new ServerException(400, StringUtils.join(check.getBlockers(), ", "));
        AgentSkill skill = requireSkill(skillId);
        AgentSkillVersion draft = requireDraft(skillId);
        List<AgentSkillToolBinding> tools = toolBindingService.list(Wrappers.lambdaQuery(AgentSkillToolBinding.class).eq(AgentSkillToolBinding::getSkillVersionId, draft.getId()));
        for (AgentSkillToolBinding binding : tools) {
            AgentTool tool = agentToolService.getById(binding.getToolId());
            if (tool == null || Boolean.TRUE.equals(tool.getDeleted()) || !Integer.valueOf(1).equals(tool.getStatus())) {
                throw new ServerException(400, I18nUtils.getMessage("skill.tool.unavailable"));
            }
        }
        // 只以已发布版本计算序号，草稿不占用正式版本号。
        int next = versionService.list(Wrappers.lambdaQuery(AgentSkillVersion.class).eq(AgentSkillVersion::getSkillId, skillId).eq(AgentSkillVersion::getStatus, 1))
                .stream().map(AgentSkillVersion::getVersionNo).filter(v -> v != null).max(Integer::compareTo).orElse(0) + 1;
        draft.setVersionNo(next);
        draft.setStatus(1);
        draft.setPublishedAt(System.currentTimeMillis());
        draft.setPublishedBy(userId);
        versionService.updateById(draft);
        skill.setCurrentVersionId(draft.getId());
        skill.setStatus(1);
        updateById(skill);
        routingIndexService.indexPublishedVersion(draft.getId());
        return draft;
    }

    @Override
    public AgentSkillDetailVo detail(String skillId) {
        AgentSkill skill = requireSkill(skillId);
        AgentSkillDetailVo result = new AgentSkillDetailVo();
        result.setSkill(skill);
        AgentSkillVersion draft = versionService.getOne(Wrappers.lambdaQuery(AgentSkillVersion.class).eq(AgentSkillVersion::getSkillId, skillId).eq(AgentSkillVersion::getStatus, 0));
        result.setDraft(draft);
        result.setCurrentVersion(StringUtils.isBlank(skill.getCurrentVersionId()) ? null : versionService.getById(skill.getCurrentVersionId()));
        String versionId = draft != null ? draft.getId() : skill.getCurrentVersionId();
        if (versionId == null) { result.setTools(Collections.emptyList()); result.setKnowledgeBases(Collections.emptyList()); result.setResources(Collections.emptyList()); return result; }
        result.setTools(toolBindingService.list(Wrappers.lambdaQuery(AgentSkillToolBinding.class).eq(AgentSkillToolBinding::getSkillVersionId, versionId)));
        result.setKnowledgeBases(knowledgeBindingService.list(Wrappers.lambdaQuery(AgentSkillKnowledgeBinding.class).eq(AgentSkillKnowledgeBinding::getSkillVersionId, versionId)));
        result.setResources(resourceService.list(Wrappers.lambdaQuery(AgentSkillResource.class).eq(AgentSkillResource::getSkillVersionId, versionId)));
        return result;
    }

    @Override
    public List<AgentSkillVersion> listVersions(String skillId) {
        requireSkill(skillId);
        return versionService.list(Wrappers.lambdaQuery(AgentSkillVersion.class).eq(AgentSkillVersion::getSkillId, skillId).orderByDesc(AgentSkillVersion::getVersionNo));
    }

    @Override public List<AgentDefinitionSkillBinding> listBindings(String agentId) { return definitionBindingService.list(Wrappers.lambdaQuery(AgentDefinitionSkillBinding.class).eq(AgentDefinitionSkillBinding::getAgentDefinitionId, agentId).orderByAsc(AgentDefinitionSkillBinding::getPriority)); }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String install(String agentId, AgentSkillInstallDto dto) {
        requireAgent(agentId);
        AgentSkillVersion version = requirePublishedVersion(dto.getSkillVersionId());
        if (definitionBindingService.count(Wrappers.lambdaQuery(AgentDefinitionSkillBinding.class).eq(AgentDefinitionSkillBinding::getAgentDefinitionId, agentId).eq(AgentDefinitionSkillBinding::getSkillId, version.getSkillId())) > 0) throw new ServerException(409, I18nUtils.getMessage("skill.installation.already-exists"));
        AgentDefinitionSkillBinding binding = new AgentDefinitionSkillBinding(); binding.setAgentDefinitionId(agentId); binding.setSkillId(version.getSkillId()); binding.setSkillVersionId(version.getId()); binding.setPriority(dto.getPriority()); binding.setStatus(dto.getStatus() == null ? 1 : dto.getStatus()); binding.setConfigOverrides(dto.getConfigOverrides()); definitionBindingService.save(binding); return binding.getId();
    }

    @Override public void updateBinding(String agentId, String bindingId, AgentSkillBindingUpdateDto dto) {
        AgentDefinitionSkillBinding binding = requireBinding(agentId, bindingId);
        if (StringUtils.isNotBlank(dto.getSkillVersionId())) { AgentSkillVersion version = requirePublishedVersion(dto.getSkillVersionId()); if (!version.getSkillId().equals(binding.getSkillId())) throw new ServerException(400, I18nUtils.getMessage("skill.installation.version.mismatch")); binding.setSkillVersionId(version.getId()); }
        if (dto.getPriority() != null) binding.setPriority(dto.getPriority()); if (dto.getStatus() != null) binding.setStatus(dto.getStatus()); if (dto.getConfigOverrides() != null) binding.setConfigOverrides(dto.getConfigOverrides()); definitionBindingService.updateById(binding);
    }
    @Override public void removeBinding(String agentId, String bindingId) { definitionBindingService.removeById(requireBinding(agentId, bindingId).getId()); }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentSkillResource uploadResource(String skillId, String fileName, String contentType, byte[] content, String purpose, String type) {
        requireSkill(skillId);
        AgentSkillVersion draft = requireDraft(skillId);
        if (content == null || content.length == 0) throw new ServerException(400, I18nUtils.getMessage("skill.resource.file.required"));
        if (content.length > maxResourceSize) throw new ServerException(400, I18nUtils.getMessage("skill.resource.file.size-exceeded"));
        String[] parsed = parseResource(fileName, type);
        if (resourceService.count(Wrappers.lambdaQuery(AgentSkillResource.class).eq(AgentSkillResource::getSkillVersionId, draft.getId())) >= MAX_DRAFT_RESOURCE_COUNT) {
            throw new ServerException(400, I18nUtils.getMessage("skill.resource.count-exceeded"));
        }
        String sha256 = sha256Hex(content);
        // 不可覆盖对象键：skills/{skillId}/{versionId}/{sha256}，同内容上传幂等复用。
        String objectKey = "skills/" + skillId + "/" + draft.getId() + "/" + sha256;
        AgentSkillResource existing = resourceService.getOne(Wrappers.lambdaQuery(AgentSkillResource.class).eq(AgentSkillResource::getObjectKey, objectKey).eq(AgentSkillResource::getSkillVersionId, draft.getId()));
        if (existing != null) throw new ServerException(409, I18nUtils.getMessage("skill.resource.duplicate"));
        objectStorageService.upload(resourceBucket, objectKey, content, StringUtils.defaultIfBlank(contentType, "application/octet-stream"));
        AgentSkillResource resource = new AgentSkillResource();
        resource.setSkillVersionId(draft.getId());
        resource.setName(fileName);
        resource.setType(parsed[0]);
        resource.setLanguage(parsed[1]);
        resource.setObjectKey(objectKey);
        resource.setContentSha256(sha256);
        resource.setSize((long) content.length);
        resource.setPurpose(StringUtils.trimToNull(purpose));
        resource.setStatus(1);
        resourceService.save(resource);
        return resource;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentSkillResource updateDraftResource(String skillId, String resourceId, String fileName, String contentType, byte[] content, String purpose, String type) {
        AgentSkillVersion draft = requireDraft(skillId);
        AgentSkillResource resource = resourceService.getById(resourceId);
        if (resource == null || !draft.getId().equals(resource.getSkillVersionId())) throw new ServerException(404, I18nUtils.getMessage("skill.resource.not-found"));
        if (content == null || content.length == 0 || content.length > maxResourceSize) throw new ServerException(400, I18nUtils.getMessage("skill.resource.file.size-exceeded"));
        String[] parsed = parseResource(fileName, type);
        String newObjectKey = "skills/" + skillId + "/" + draft.getId() + "/" + sha256Hex(content);
        objectStorageService.upload(resourceBucket, newObjectKey, content, StringUtils.defaultIfBlank(contentType, "application/octet-stream"));
        resource.setName(fileName); resource.setType(parsed[0]); resource.setLanguage(parsed[1]); resource.setObjectKey(newObjectKey);
        resource.setContentSha256(sha256Hex(content)); resource.setSize((long) content.length); resource.setPurpose(StringUtils.trimToNull(purpose));
        resourceService.updateById(resource);
        return resource;
    }

    @Override
    public List<AgentSkillResource> listResources(String skillId) {
        requireSkill(skillId);
        AgentSkillVersion draft = versionService.getOne(Wrappers.lambdaQuery(AgentSkillVersion.class).eq(AgentSkillVersion::getSkillId, skillId).eq(AgentSkillVersion::getStatus, 0));
        String versionId = draft != null ? draft.getId() : getById(skillId).getCurrentVersionId();
        if (versionId == null) return Collections.emptyList();
        return resourceService.list(Wrappers.lambdaQuery(AgentSkillResource.class).eq(AgentSkillResource::getSkillVersionId, versionId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeDraftResource(String skillId, String resourceId) {
        AgentSkillVersion draft = requireDraft(skillId);
        AgentSkillResource resource = resourceService.getById(resourceId);
        if (resource == null || !draft.getId().equals(resource.getSkillVersionId())) {
            throw new ServerException(404, I18nUtils.getMessage("skill.resource.not-found"));
        }
        // 文件生成已迁移到平台通用渲染器，Skill 资源不再作为可执行入口，
        // 历史执行配置不能阻止用户删除草稿中的脚本或模板资源。
        resourceService.removeById(resourceId);
        try {
            objectStorageService.removeObject(resourceBucket, resource.getObjectKey());
        } catch (RuntimeException e) {
            // 对象清理失败不影响数据库记录删除，避免草稿编辑被存储故障阻塞。
        }
    }

    @Override
    public AgentSkillPreviewVo preview(String skillId, AgentSkillPreviewDto dto) {
        AgentSkill skill = requireSkill(skillId);
        AgentSkillVersion version = versionService.getOne(Wrappers.lambdaQuery(AgentSkillVersion.class).eq(AgentSkillVersion::getSkillId, skillId).eq(AgentSkillVersion::getStatus, 0));
        if (version == null) version = StringUtils.isBlank(skill.getCurrentVersionId()) ? null : versionService.getById(skill.getCurrentVersionId());
        if (version == null) throw new ServerException(409, I18nUtils.getMessage("skill.resource.no-version"));

        AgentSkillPreviewVo result = new AgentSkillPreviewVo();
        result.setSkillId(skill.getId());
        result.setSkillCode(skill.getCode());
        result.setSkillName(skill.getName());
        result.setVersionNo(version.getVersionNo());
        result.setVersionStatus(version.getStatus());

        List<AgentSkillToolBinding> toolBindings = toolBindingService.list(Wrappers.lambdaQuery(AgentSkillToolBinding.class).eq(AgentSkillToolBinding::getSkillVersionId, version.getId()));
        List<AgentSkillPreviewVo.ToolItem> toolItems = new ArrayList<>();
        for (AgentSkillToolBinding binding : toolBindings) {
            AgentTool tool = agentToolService.getById(binding.getToolId());
            AgentSkillPreviewVo.ToolItem item = new AgentSkillPreviewVo.ToolItem();
            item.setToolId(binding.getToolId());
            item.setRequired(binding.getRequired());
            item.setPriority(binding.getPriority());
            if (tool != null) {
                item.setToolName(tool.getName());
                item.setToolCode(tool.getCode());
                item.setAvailable(!Boolean.TRUE.equals(tool.getDeleted()) && Integer.valueOf(1).equals(tool.getStatus()));
            } else {
                item.setToolName(binding.getToolId());
                item.setAvailable(false);
            }
            toolItems.add(item);
        }
        result.setTools(toolItems);

        List<AgentSkillKnowledgeBinding> knowledgeBindings = knowledgeBindingService.list(Wrappers.lambdaQuery(AgentSkillKnowledgeBinding.class).eq(AgentSkillKnowledgeBinding::getSkillVersionId, version.getId()));
        List<String> knowledgeIds = new ArrayList<>();
        for (AgentSkillKnowledgeBinding binding : knowledgeBindings) knowledgeIds.add(binding.getKnowledgeBaseId());
        result.setKnowledgeBaseIds(knowledgeIds);

        List<AgentSkillResource> resources = resourceService.list(Wrappers.lambdaQuery(AgentSkillResource.class).eq(AgentSkillResource::getSkillVersionId, version.getId()));
        List<AgentSkillPreviewVo.ResourceItem> resourceItems = new ArrayList<>();
        for (AgentSkillResource resource : resources) {
            AgentSkillPreviewVo.ResourceItem item = new AgentSkillPreviewVo.ResourceItem();
            item.setResourceId(resource.getId());
            item.setName(resource.getName());
            item.setType(resource.getType());
            item.setLanguage(resource.getLanguage());
            item.setSize(resource.getSize());
            item.setPurpose(resource.getPurpose());
            item.setContentSha256(resource.getContentSha256());
            resourceItems.add(item);
        }
        result.setResources(resourceItems);

        Map<String, Map<String, Object>> inputs = dto == null || dto.getSkillInputs() == null ? Collections.emptyMap() : dto.getSkillInputs();
        String prompt = buildPreviewPrompt(skill, version, resources, inputs.get(skill.getCode()));
        result.setPrompt(prompt);
        result.setEstimatedTokens(prompt.length() / 4L);
        return result;
    }

    @Override
    public AgentSkillVo lifecycle(AgentSkill skill) {
        AgentSkillVo result = new AgentSkillVo();
        org.springframework.beans.BeanUtils.copyProperties(skill, result);
        AgentSkillVersion draft = versionService.getOne(Wrappers.lambdaQuery(AgentSkillVersion.class)
                .eq(AgentSkillVersion::getSkillId, skill.getId()).eq(AgentSkillVersion::getStatus, 0));
        result.setHasDraft(draft != null);
        AgentSkillVersion current = StringUtils.isBlank(skill.getCurrentVersionId()) ? null : versionService.getById(skill.getCurrentVersionId());
        result.setCurrentVersionNo(current == null ? null : current.getVersionNo());
        result.setInstalledAgentCount(definitionBindingService.count(Wrappers.lambdaQuery(AgentDefinitionSkillBinding.class)
                .eq(AgentDefinitionSkillBinding::getSkillId, skill.getId()).eq(AgentDefinitionSkillBinding::getStatus, 1)));
        String versionId = draft != null ? draft.getId() : skill.getCurrentVersionId();
        result.setToolCount(versionId == null ? 0L : toolBindingService.count(Wrappers.lambdaQuery(AgentSkillToolBinding.class).eq(AgentSkillToolBinding::getSkillVersionId, versionId)));
        result.setKnowledgeBaseCount(versionId == null ? 0L : knowledgeBindingService.count(Wrappers.lambdaQuery(AgentSkillKnowledgeBinding.class).eq(AgentSkillKnowledgeBinding::getSkillVersionId, versionId)));
        result.setResourceCount(versionId == null ? 0L : resourceService.count(Wrappers.lambdaQuery(AgentSkillResource.class).eq(AgentSkillResource::getSkillVersionId, versionId)));
        return result;
    }

    @Override
    public AgentSkillPublishCheckVo publishCheck(String skillId) {
        AgentSkill skill = requireSkill(skillId);
        AgentSkillPublishCheckVo result = new AgentSkillPublishCheckVo();
        AgentSkillDetailVo detail = detail(skillId);
        AgentSkillVersion draft = detail.getDraft();
        if (draft == null) {
            result.getBlockers().add("请先创建或续建草稿");
            return result;
        }
        result.setDraftVersionId(draft.getId());
        if (StringUtils.isBlank(draft.getInstruction())) result.getBlockers().add("请填写系统指令");
        if (StringUtils.isBlank(draft.getRoutingSummary())) result.getBlockers().add("请填写 Skill 发现摘要");
        else if (draft.getRoutingSummary().length() > 200) result.getBlockers().add("Skill 发现摘要不能超过 200 个字符");
        validateRoutingMetadata(draft, result);
        if (StringUtils.isBlank(skill.getDescription())) result.getWarnings().add("尚未填写技能描述，其他管理员难以理解其适用范围");
        if (StringUtils.isBlank(draft.getInputSchema())) result.getWarnings().add("尚未声明输入参数，运行时输入将不受结构约束");
        if (StringUtils.isBlank(draft.getOutputSchema())) result.getWarnings().add("尚未声明输出参数，回答格式可能不稳定");
        if (detail.getResources().isEmpty()) result.getWarnings().add("未添加资源文件；如该技能依赖制度、模板或脚本，请在发布前补充");
        Set<String> toolIds = new LinkedHashSet<>();
        for (AgentSkillToolBinding binding : detail.getTools()) {
            if (!toolIds.add(binding.getToolId())) result.getWarnings().add("工具依赖存在重复声明");
            AgentTool tool = agentToolService.getById(binding.getToolId());
            boolean available = tool != null && !Boolean.TRUE.equals(tool.getDeleted()) && Integer.valueOf(1).equals(tool.getStatus());
            if (!available && Boolean.TRUE.equals(binding.getRequired())) result.getBlockers().add("必需工具不可用：" + binding.getToolId());
            else if (!available) result.getWarnings().add("可选工具当前不可用：" + binding.getToolId());
        }
        result.setEstimatedTokens((long) (StringUtils.length(skill.getDescription()) + StringUtils.length(draft.getInstruction())) / 4L);
        result.setReady(result.getBlockers().isEmpty());
        return result;
    }

    @Override
    public AgentSkillStatisticsVo statistics() {
        AgentSkillStatisticsVo result = new AgentSkillStatisticsVo();
        result.setTotalCount(count(Wrappers.lambdaQuery(AgentSkill.class).eq(AgentSkill::getDeleted, false)));
        result.setEnabledCount(count(Wrappers.lambdaQuery(AgentSkill.class).eq(AgentSkill::getDeleted, false).eq(AgentSkill::getStatus, 1)));
        result.setDraftCount(versionService.count(Wrappers.lambdaQuery(AgentSkillVersion.class).eq(AgentSkillVersion::getStatus, 0)));
        result.setPublishedCount(count(Wrappers.lambdaQuery(AgentSkill.class).eq(AgentSkill::getDeleted, false).isNotNull(AgentSkill::getCurrentVersionId)));
        result.setBoundAgentCount(definitionBindingService.count(Wrappers.lambdaQuery(AgentDefinitionSkillBinding.class).eq(AgentDefinitionSkillBinding::getStatus, 1)));
        return result;
    }

    private String buildPreviewPrompt(AgentSkill skill, AgentSkillVersion version, List<AgentSkillResource> resources, Map<String, Object> sampleInput) {
        StringBuilder prompt = new StringBuilder("[Installed Skill]\n## ").append(skill.getName())
                .append(version.getVersionNo() == null ? "（草稿）" : " v" + version.getVersionNo());
        if (StringUtils.isNotBlank(skill.getDescription())) prompt.append("\nPurpose: ").append(skill.getDescription());
        if (StringUtils.isNotBlank(version.getInstruction())) prompt.append("\nInstructions:\n").append(version.getInstruction());
        if (resources != null && !resources.isEmpty()) {
            prompt.append("\n\n## 资源参考");
            for (AgentSkillResource resource : resources) {
                prompt.append("\n- ").append(resource.getName()).append(" (").append(resource.getType()).append(")");
                if (StringUtils.isNotBlank(resource.getPurpose())) prompt.append("：").append(resource.getPurpose());
            }
        }
        if (sampleInput != null && !sampleInput.isEmpty()) {
            prompt.append("\n\nValidated inputs:\n").append(com.alibaba.fastjson2.JSON.toJSONString(sampleInput));
        }
        prompt.append("\n\n[Platform Constraints]\n工具审批、安全与审计由平台统一控制。引用知识库资料时标注编号。");
        return prompt.toString();
    }

    /** 返回 [type, language]；按扩展名白名单解析，拒绝压缩包与未知类型。 */
    private String[] parseResource(String fileName, String declaredType) {
        String name = StringUtils.trimToNull(fileName);
        if (name == null || name.indexOf('.') < 0) throw new ServerException(400, I18nUtils.getMessage("skill.resource.file.unsupported-type"));
        String lower = name.toLowerCase(Locale.ROOT);
        String[] parsed;
        if (lower.endsWith(".md")) parsed = new String[]{"MARKDOWN", null};
        else if (lower.endsWith(".html") || lower.endsWith(".hbs") || lower.endsWith(".tpl") || lower.endsWith(".ftl")) parsed = new String[]{"TEMPLATE", null};
        else throw new ServerException(400, I18nUtils.getMessage("skill.resource.file.unsupported-type"));
        if (StringUtils.isNotBlank(declaredType) && !declaredType.equalsIgnoreCase(parsed[0])) {
            throw new ServerException(400, I18nUtils.getMessage("skill.resource.file.type-mismatch"));
        }
        return parsed;
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new ServerException(500, I18nUtils.getMessage("server.error"));
        }
    }

    private void replaceDraftBindings(String versionId, AgentSkillDraftDto dto) {
        // 仅草稿允许整体替换依赖声明；已发布版本不会调用此方法。
        toolBindingService.remove(Wrappers.lambdaQuery(AgentSkillToolBinding.class).eq(AgentSkillToolBinding::getSkillVersionId, versionId));
        knowledgeBindingService.remove(Wrappers.lambdaQuery(AgentSkillKnowledgeBinding.class).eq(AgentSkillKnowledgeBinding::getSkillVersionId, versionId));
        if (dto.getTools() != null) for (AgentSkillToolDto item : dto.getTools()) { if (StringUtils.isBlank(item.getToolId())) throw new ServerException(400, I18nUtils.getMessage("skill.binding.tool-id.required")); AgentSkillToolBinding binding = new AgentSkillToolBinding(); binding.setSkillVersionId(versionId); binding.setToolId(item.getToolId()); binding.setRequired(Boolean.TRUE.equals(item.getRequired())); binding.setPriority(item.getPriority()); toolBindingService.save(binding); }
        if (dto.getKnowledgeBaseIds() != null) for (String id : dto.getKnowledgeBaseIds()) { if (StringUtils.isBlank(id)) throw new ServerException(400, I18nUtils.getMessage("skill.binding.knowledge-base-id.required")); AgentSkillKnowledgeBinding binding = new AgentSkillKnowledgeBinding(); binding.setSkillVersionId(versionId); binding.setKnowledgeBaseId(id); knowledgeBindingService.save(binding); }
    }
    private AgentSkill requireSkill(String id) { AgentSkill value = getById(id); if (value == null || Boolean.TRUE.equals(value.getDeleted())) throw new ServerException(404, I18nUtils.getMessage("skill.not-found")); return value; }
    private AgentSkillVersion requireDraft(String skillId) { AgentSkillVersion value = versionService.getOne(Wrappers.lambdaQuery(AgentSkillVersion.class).eq(AgentSkillVersion::getSkillId, skillId).eq(AgentSkillVersion::getStatus, 0)); if (value == null) throw new ServerException(409, I18nUtils.getMessage("skill.draft.not-found")); return value; }
    private AgentSkillVersion requirePublishedVersion(String id) { AgentSkillVersion value = versionService.getById(id); if (value == null || !Integer.valueOf(1).equals(value.getStatus())) throw new ServerException(400, I18nUtils.getMessage("skill.version.not-published")); AgentSkill skill = requireSkill(value.getSkillId()); if (!Integer.valueOf(1).equals(skill.getStatus())) throw new ServerException(400, I18nUtils.getMessage("skill.disabled")); return value; }
    private AgentDefinitionSkillBinding requireBinding(String agentId, String id) { AgentDefinitionSkillBinding value = definitionBindingService.getById(id); if (value == null || !agentId.equals(value.getAgentDefinitionId())) throw new ServerException(404, I18nUtils.getMessage("skill.installation.not-found")); return value; }
    private void requireAgent(String id) { AgentDefinition agent = agentDefinitionService.getById(id); if (agent == null || Boolean.TRUE.equals(agent.getDeleted())) throw new ServerException(404, I18nUtils.getMessage("skill.agent.not-found")); }
    private void validateIdentity(AgentSkillDraftDto dto) { if (dto == null || StringUtils.isBlank(dto.getName()) || StringUtils.isBlank(dto.getCode())) throw new ServerException(400, I18nUtils.getMessage("skill.identity.required")); }
    private void applyIdentity(AgentSkill target, AgentSkillDraftDto source) { target.setName(source.getName()); target.setCode(source.getCode()); target.setDescription(source.getDescription()); target.setCategory(source.getCategory()); target.setIcon(source.getIcon()); target.setTags(source.getTags()); }
    private void applyDraft(AgentSkillVersion target, AgentSkillDraftDto source) { target.setInstruction(source.getInstruction()); target.setInputSchema(source.getInputSchema()); target.setOutputSchema(source.getOutputSchema()); target.setToolPolicy(source.getToolPolicy()); target.setChangeNote(source.getChangeNote()); target.setRoutingSummary(source.getRoutingSummary()); target.setTriggerTerms(JSON.toJSONString(source.getTriggerTerms() == null ? Collections.emptyList() : source.getTriggerTerms())); target.setExcludeTerms(JSON.toJSONString(source.getExcludeTerms() == null ? Collections.emptyList() : source.getExcludeTerms())); target.setRoutingExamples(JSON.toJSONString(source.getRoutingExamples() == null ? Collections.emptyList() : source.getRoutingExamples())); }
    private void validateRoutingMetadata(AgentSkillVersion draft, AgentSkillPublishCheckVo result) { try { List<String> trigger = JSON.parseArray(StringUtils.defaultIfBlank(draft.getTriggerTerms(), "[]"), String.class); List<String> exclude = JSON.parseArray(StringUtils.defaultIfBlank(draft.getExcludeTerms(), "[]"), String.class); List<String> examples = JSON.parseArray(StringUtils.defaultIfBlank(draft.getRoutingExamples(), "[]"), String.class); if (trigger.size() > 20 || exclude.size() > 20 || examples.size() > 5) result.getBlockers().add("路由关键词最多 20 个，示例最多 5 个"); for (String value : trigger) if (exclude.contains(value)) result.getBlockers().add("触发词和排除词不能重复：" + value); } catch (Exception e) { result.getBlockers().add("路由发现配置必须是有效列表"); } }
}
