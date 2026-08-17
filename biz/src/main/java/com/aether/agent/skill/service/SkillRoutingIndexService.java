package com.aether.agent.skill.service;

import com.aether.agent.entity.ModelProvider;
import com.aether.agent.service.ModelCatalogService;
import com.aether.agent.skill.entity.AgentSkill;
import com.aether.agent.skill.entity.AgentSkillRoutingIndex;
import com.aether.agent.skill.entity.AgentSkillVersion;
import com.aether.agent.skill.service.impl.AgentSkillVersionServiceImpl;
import com.aether.agent.skill.mapper.AgentSkillMapper;
import com.aether.agent.skill.mapper.AgentSkillRoutingIndexMapper;
import com.aether.knowledge.service.KnowledgeEmbeddingService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 定义SkillRouting索引业务服务契约。
 */
@Service
public class SkillRoutingIndexService {
    private final AgentSkillVersionServiceImpl versionService;
    private final AgentSkillMapper skillMapper;
    private final AgentSkillRoutingIndexMapper mapper;
    private final KnowledgeEmbeddingService embeddingService;
    private final ModelCatalogService modelCatalogService;
    private final SkillRoutingConfigService routingConfigService;
    private final TaskExecutor taskExecutor;

    /**
     * 创建 {@code SkillRoutingIndexService} 实例。
     */
    public SkillRoutingIndexService(AgentSkillVersionServiceImpl versionService, AgentSkillMapper skillMapper, AgentSkillRoutingIndexMapper mapper, KnowledgeEmbeddingService embeddingService, ModelCatalogService modelCatalogService, SkillRoutingConfigService routingConfigService, @Qualifier("asyncPoolTaskExecutor") TaskExecutor taskExecutor) {
        this.versionService = versionService;
        this.skillMapper = skillMapper;
        this.mapper = mapper;
        this.embeddingService = embeddingService;
        this.modelCatalogService = modelCatalogService;
        this.routingConfigService = routingConfigService;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Rebuild every published version after an embedding Provider is changed.
     */
    public void reindexPublishedVersions() {
        // Do not compare a query from the newly selected Provider with vectors from the old one.
        // BlockAttackInnerInterceptor rejects broad mapper updates even with the logic-delete predicate.
        // This operation is rare (only Provider changes), so update by primary key explicitly.
        for (AgentSkillRoutingIndex index : mapper.selectList(Wrappers.lambdaQuery(AgentSkillRoutingIndex.class)
                .eq(AgentSkillRoutingIndex::getDeleted, false))) {
            AgentSkillRoutingIndex status = new AgentSkillRoutingIndex();
            status.setId(index.getId());
            status.setIndexStatus(0);
            mapper.updateById(status);
        }
        for (AgentSkillVersion version : versionService.list(Wrappers.lambdaQuery(AgentSkillVersion.class).eq(AgentSkillVersion::getStatus, 1)))
            taskExecutor.execute(() -> indexPublishedVersion(version.getId()));
    }

    /**
     * 索引PublishedVersion。
     */
    @Async("asyncPoolTaskExecutor")
    public void indexPublishedVersion(String versionId) {
        AgentSkillVersion version = versionService.getById(versionId);
        if (version == null || !Integer.valueOf(1).equals(version.getStatus())) return;
        AgentSkill skill = skillMapper.selectById(version.getSkillId());
        if (skill == null) return;
        String content = skill.getName() + "\n" + StringUtils.defaultString(version.getRoutingSummary()) + "\n" + StringUtils.defaultString(skill.getCategory()) + "\n" + StringUtils.defaultString(skill.getTags()) + "\n" + StringUtils.defaultString(version.getTriggerTerms()) + "\n" + StringUtils.defaultString(version.getRoutingExamples());
        AgentSkillRoutingIndex index = mapper.selectOne(Wrappers.lambdaQuery(AgentSkillRoutingIndex.class).eq(AgentSkillRoutingIndex::getSkillVersionId, versionId));
        if (index == null) {
            index = new AgentSkillRoutingIndex();
            index.setSkillVersionId(versionId);
        }
        index.setContentHash(sha256(content));
        try {
            String modelId = routingConfigService.embeddingModelId();
            if (StringUtils.isBlank(modelId)) throw new IllegalStateException("embedding model is not configured");
            ModelProvider provider = modelCatalogService.resolveProvider(modelId, "EMBEDDING");
            index.setEmbedding(embeddingService.toVectorLiteral(embeddingService.embed(provider, content)));
            index.setEmbeddingProviderId(provider.getId());
            index.setEmbeddingModel(provider.getDefaultModel());
            index.setIndexStatus(1);
            index.setFailureReason(null);
            index.setIndexedAt(System.currentTimeMillis());
        } catch (Exception e) {
            index.setIndexStatus(2);
            index.setFailureReason(StringUtils.abbreviate(e.getMessage(), 512));
        }
        if (StringUtils.isBlank(index.getId())) {
            long now = System.currentTimeMillis();
            index.setId(IdWorker.getIdStr());
            index.setCreatedAt(now);
            index.setUpdatedAt(now);
            if (index.getSortNum() == null) index.setSortNum(1);
            if (index.getDeleted() == null) index.setDeleted(false);
            if (index.getState() == null) index.setState(0);
            mapper.insertVectorIndex(index);
        } else {
            index.setUpdatedAt(System.currentTimeMillis());
            mapper.updateVectorIndex(index);
        }
    }

    /**
     * 处理sha256。
     */
    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hash) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
