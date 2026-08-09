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
import com.aether.exception.ServerException;
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
    private final ModelClientFactory modelClientFactory;
    private final String resourceBucket;

    public SkillResourceWorkbenchService(AgentSkillServiceImpl skillService,
                                         AgentSkillResourceServiceImpl resourceService,
                                         ObjectStorageService storage,
                                         ModelProviderService providerService,
                                         ModelClientFactory modelClientFactory,
                                         @Value("${skill.storage.bucket:${MINIO_SKILL_BUCKET:aether-skill}}") String resourceBucket) {
        this.skillService = skillService;
        this.resourceService = resourceService;
        this.storage = storage;
        this.providerService = providerService;
        this.modelClientFactory = modelClientFactory;
        this.resourceBucket = resourceBucket;
    }

    public String content(String skillId, String resourceId) {
        AgentSkillResource resource = skillService.listResources(skillId).stream()
                .filter(item -> resourceId.equals(item.getId())).findFirst()
                .orElseThrow(() -> new ServerException(404, "skill.resource.not-found"));
        if (resource.getSize() != null && resource.getSize() > MAX_PREVIEW_BYTES) {
            throw new ServerException(413, "Resource is too large to preview online");
        }
        byte[] bytes = storage.getObject(resourceBucket, resource.getObjectKey());
        if (bytes.length > MAX_PREVIEW_BYTES) throw new ServerException(413, "Resource is too large to preview online");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public AgentSkillResourceGenerateVo generate(String skillId, AgentSkillResourceGenerateDto dto) {
        skillService.getById(skillId);
        if (dto == null || StringUtils.isBlank(dto.getProviderId()) || StringUtils.isBlank(dto.getPrompt())) {
            throw new ServerException(400, "AI provider and generation request are required");
        }
        String type = StringUtils.upperCase(dto.getType());
        if (!Arrays.asList("MARKDOWN", "TEMPLATE", "SCRIPT").contains(type)) {
            throw new ServerException(400, "Unsupported resource type");
        }
        ModelProvider provider = providerService.getById(dto.getProviderId());
        if (provider == null || !Integer.valueOf(1).equals(provider.getStatus())) {
            throw new ServerException(400, "Selected AI provider is unavailable");
        }
        String name = normaliseName(dto.getName(), type);
        ModelChatRequest request = new ModelChatRequest();
        request.setProvider(provider);
        request.setModel(StringUtils.defaultIfBlank(dto.getModel(), provider.getDefaultModel()));
        request.setMaxCompletionTokens(4096);
        request.setMessages(Arrays.asList(
                new ModelChatMessage("system", "You author a single Skill resource. Return only the file content, without Markdown fences or explanation. Follow the requested type exactly. For scripts, use only standard library APIs and read JSON from stdin, writing files only under ./output."),
                new ModelChatMessage("user", "Resource name: " + name + "\nType: " + type + "\nPurpose: " + StringUtils.defaultString(dto.getPurpose()) + "\nRequest:\n" + dto.getPrompt())
        ));
        ModelClient client = modelClientFactory.getClient(provider);
        ModelChatResponse response = client.chatByProvider(request);
        if (response == null || StringUtils.isBlank(response.getContent())) throw new ServerException(502, "AI returned an empty resource draft");
        AgentSkillResourceGenerateVo result = new AgentSkillResourceGenerateVo();
        result.setName(name); result.setType(type); result.setPurpose(dto.getPurpose());
        result.setContent(stripFence(response.getContent())); result.setModel(response.getModel());
        return result;
    }

    private String normaliseName(String name, String type) {
        String extension = "MARKDOWN".equals(type) ? ".md" : "SCRIPT".equals(type) ? ".py" : ".hbs";
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
