package com.aether.agent.skill.service;

import com.aether.agent.entity.ModelProvider;
import com.aether.agent.service.ModelProviderService;
import com.aether.agent.skill.entity.AgentSkill;
import com.aether.agent.skill.entity.AgentSkillRoutingIndex;
import com.aether.agent.skill.entity.AgentSkillVersion;
import com.aether.agent.skill.service.impl.AgentSkillVersionServiceImpl;
import com.aether.agent.skill.mapper.AgentSkillMapper;
import com.aether.agent.skill.mapper.AgentSkillRoutingIndexMapper;
import com.aether.knowledge.service.KnowledgeEmbeddingService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class SkillRoutingIndexService {
    private final AgentSkillVersionServiceImpl versionService;
    private final AgentSkillMapper skillMapper;
    private final AgentSkillRoutingIndexMapper mapper;
    private final KnowledgeEmbeddingService embeddingService;
    private final ModelProviderService providerService;
    private final SkillRoutingConfigService routingConfigService;
    private final TaskExecutor taskExecutor;
    public SkillRoutingIndexService(AgentSkillVersionServiceImpl versionService, AgentSkillMapper skillMapper, AgentSkillRoutingIndexMapper mapper, KnowledgeEmbeddingService embeddingService, ModelProviderService providerService, SkillRoutingConfigService routingConfigService, @Qualifier("asyncPoolTaskExecutor") TaskExecutor taskExecutor) { this.versionService=versionService;this.skillMapper=skillMapper;this.mapper=mapper;this.embeddingService=embeddingService;this.providerService=providerService;this.routingConfigService=routingConfigService;this.taskExecutor=taskExecutor; }
    /** Rebuild every published version after an embedding Provider is changed. */
    public void reindexPublishedVersions() {
        // Do not compare a query from the newly selected Provider with vectors from the old one.
        // BlockAttackInnerInterceptor rejects broad mapper updates even with the logic-delete predicate.
        // This operation is rare (only Provider changes), so update by primary key explicitly.
        for (AgentSkillRoutingIndex index : mapper.selectList(Wrappers.lambdaQuery(AgentSkillRoutingIndex.class)
                .eq(AgentSkillRoutingIndex::getDeleted, false))) {
            index.setIndexStatus(0);
            mapper.updateById(index);
        }
        for (AgentSkillVersion version : versionService.list(Wrappers.lambdaQuery(AgentSkillVersion.class).eq(AgentSkillVersion::getStatus, 1))) taskExecutor.execute(() -> indexPublishedVersion(version.getId()));
    }
    @Async("asyncPoolTaskExecutor")
    public void indexPublishedVersion(String versionId) {
        AgentSkillVersion version = versionService.getById(versionId); if (version == null || !Integer.valueOf(1).equals(version.getStatus())) return;
        AgentSkill skill = skillMapper.selectById(version.getSkillId()); if (skill == null) return;
        String content = skill.getName()+"\n"+StringUtils.defaultString(version.getRoutingSummary())+"\n"+StringUtils.defaultString(skill.getCategory())+"\n"+StringUtils.defaultString(skill.getTags())+"\n"+StringUtils.defaultString(version.getTriggerTerms())+"\n"+StringUtils.defaultString(version.getRoutingExamples());
        AgentSkillRoutingIndex index = mapper.selectOne(Wrappers.lambdaQuery(AgentSkillRoutingIndex.class).eq(AgentSkillRoutingIndex::getSkillVersionId, versionId)); if (index == null) { index = new AgentSkillRoutingIndex(); index.setSkillVersionId(versionId); }
        index.setContentHash(sha256(content));
        try { String providerId = routingConfigService.embeddingProviderId(); if (StringUtils.isBlank(providerId)) throw new IllegalStateException("embedding provider is not configured"); ModelProvider provider=providerService.getById(providerId); if(provider==null||!Integer.valueOf(1).equals(provider.getStatus())) throw new IllegalStateException("embedding provider is unavailable"); index.setEmbedding(embeddingService.toVectorLiteral(embeddingService.embed(provider, content))); index.setEmbeddingProviderId(provider.getId()); index.setEmbeddingModel(provider.getDefaultModel()); index.setIndexStatus(1); index.setFailureReason(null); index.setIndexedAt(System.currentTimeMillis()); } catch (Exception e) { index.setIndexStatus(2); index.setFailureReason(StringUtils.abbreviate(e.getMessage(), 512)); }
        if (StringUtils.isBlank(index.getId())) mapper.insert(index); else mapper.updateById(index);
    }
    private String sha256(String value) { try { byte[] hash=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder out=new StringBuilder(); for(byte b:hash)out.append(String.format("%02x",b)); return out.toString(); } catch(Exception e) { throw new IllegalStateException(e); } }
}
