package com.aether.agent.skill.service;

import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatMessage;
import com.aether.agent.model.ModelChatRequest;
import com.aether.agent.model.ModelChatResponse;
import com.aether.agent.model.ModelClient;
import com.aether.agent.model.ModelClientFactory;
import com.aether.agent.skill.dto.AgentSkillResourceGenerateDto;
import com.aether.agent.skill.entity.AgentSkillResource;
import com.aether.agent.skill.service.impl.AgentSkillResourceServiceImpl;
import com.aether.agent.skill.service.impl.AgentSkillServiceImpl;
import com.aether.agent.skill.vo.AgentSkillResourceGenerateVo;
import com.aether.agent.service.ModelProviderService;
import com.aether.agent.service.ModelCatalogService;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.storage.service.ObjectStorageService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Authoring-only workbench for reading and drafting resource content. */
@Service
public class SkillResourceWorkbenchService {
    private static final int MAX_PREVIEW_BYTES = 1024 * 1024;
    private final AgentSkillServiceImpl skillService;
    private final AgentSkillResourceServiceImpl resourceService;
    private final ObjectStorageService storage;
    private final ModelProviderService providerService;
    private final ModelCatalogService modelCatalogService;
    private final ModelClientFactory modelClientFactory;
    private final String resourceBucket;

    public SkillResourceWorkbenchService(AgentSkillServiceImpl skillService,
                                         AgentSkillResourceServiceImpl resourceService,
                                         ObjectStorageService storage,
                                         ModelProviderService providerService,
                                         ModelCatalogService modelCatalogService,
                                         ModelClientFactory modelClientFactory,
                                         @Value("${skill.storage.bucket:${MINIO_SKILL_BUCKET:aether-skill}}") String resourceBucket) {
        this.skillService = skillService;
        this.resourceService = resourceService;
        this.storage = storage;
        this.providerService = providerService;
        this.modelCatalogService = modelCatalogService;
        this.modelClientFactory = modelClientFactory;
        this.resourceBucket = resourceBucket;
    }

    public String content(String skillId, String resourceId) {
        AgentSkillResource resource = skillService.listResources(skillId).stream()
                .filter(item -> resourceId.equals(item.getId())).findFirst()
                .orElseThrow(() -> new ServerException(404, I18nUtils.getMessage("skill.resource.not-found")));
        if (resource.getSize() != null && resource.getSize() > MAX_PREVIEW_BYTES) {
            throw new ServerException(413, I18nUtils.getMessage("skill.resource.preview.too-large"));
        }
        byte[] bytes = storage.getObject(resourceBucket, resource.getObjectKey());
        if (bytes.length > MAX_PREVIEW_BYTES) throw new ServerException(413, I18nUtils.getMessage("skill.resource.preview.too-large"));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public AgentSkillResourceGenerateVo generate(String skillId, AgentSkillResourceGenerateDto dto) {
        skillService.getById(skillId);
        if (dto == null || StringUtils.isBlank(dto.getModelId()) || StringUtils.isBlank(dto.getPrompt())) {
            throw new ServerException(400, I18nUtils.getMessage("skill.resource.generate.provider-request.required"));
        }
        String type = StringUtils.upperCase(dto.getType());
        if (!Arrays.asList("MARKDOWN", "TEMPLATE").contains(type)) {
            throw new ServerException(400, I18nUtils.getMessage("skill.resource.type.unsupported"));
        }
        ModelProvider provider = modelCatalogService.resolveProvider(dto.getModelId(), "CHAT,MULTIMODAL");
        String name = normaliseName(dto.getName(), type);
        ModelChatRequest request = new ModelChatRequest();
        request.setProvider(provider);
        request.setModel(provider.getDefaultModel());
        request.setMaxCompletionTokens(4096);
        request.setMessages(Arrays.asList(
                new ModelChatMessage("system", "You author a single Skill reference resource. Return only the file content, without Markdown fences or explanation. Follow the requested type exactly."),
                new ModelChatMessage("user", "Resource name: " + name + "\nType: " + type + "\nPurpose: " + StringUtils.defaultString(dto.getPurpose()) + "\nRequest:\n" + dto.getPrompt())
        ));
        ModelClient client = modelClientFactory.getClient(provider);
        ModelChatResponse response = client.chatByProvider(request);
        if (response == null || StringUtils.isBlank(response.getContent())) throw new ServerException(502, I18nUtils.getMessage("skill.resource.generate.empty-response"));
        AgentSkillResourceGenerateVo result = new AgentSkillResourceGenerateVo();
        result.setName(name); result.setType(type); result.setPurpose(dto.getPurpose());
        result.setContent(stripFence(response.getContent())); result.setModel(response.getModel());
        return result;
    }

    private String normaliseName(String name, String type) {
        String extension = "MARKDOWN".equals(type) ? ".md" : ".hbs";
        String value = StringUtils.defaultIfBlank(StringUtils.trimToNull(name), "generated-resource" + extension);
        return value.contains(".") ? value : value + extension;
    }

    private String stripFence(String content) {
        String value = StringUtils.trim(content);
        if (!value.startsWith("```")) return value;
        int firstLineEnd = value.indexOf('\n');
        int lastFence = value.lastIndexOf("```");
        return firstLineEnd >= 0 && lastFence > firstLineEnd ? value.substring(firstLineEnd + 1, lastFence).trim() : value;
    }
}
