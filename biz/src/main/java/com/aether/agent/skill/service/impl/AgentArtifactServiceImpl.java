package com.aether.agent.skill.service.impl;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.skill.dto.AgentArtifactQueryDto;
import com.aether.agent.skill.entity.AgentArtifact;
import com.aether.agent.skill.mapper.AgentArtifactMapper;
import com.aether.agent.skill.service.AgentArtifactService;
import com.aether.agent.skill.vo.AgentArtifactVo;
import com.aether.local.CurrentUser;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.storage.service.ObjectStorageService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 实现智能体Artifact业务服务。
 */
@Service
public class AgentArtifactServiceImpl extends ServiceImpl<AgentArtifactMapper, AgentArtifact> implements AgentArtifactService {
    private static final long RECYCLE_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000;
    private static final Logger log = LoggerFactory.getLogger(AgentArtifactServiceImpl.class);

    private final AgentDefinitionService agentDefinitionService;
    private final ObjectStorageService objectStorageService;
    private final String artifactBucket;

    /**
     * 创建 {@code AgentArtifactServiceImpl} 实例。
     */
    public AgentArtifactServiceImpl(AgentDefinitionService agentDefinitionService,
                                    ObjectStorageService objectStorageService,
                                    @Value("${artifact.storage.bucket:${MINIO_CHAT_ATTACHMENT_BUCKET:aether-chat}}") String artifactBucket) {
        this.agentDefinitionService = agentDefinitionService;
        this.objectStorageService = objectStorageService;
        this.artifactBucket = artifactBucket;
    }

    /**
     * 分页查询Owned。
     */
    @Override
    public Page<AgentArtifactVo> pageOwned(String userId, AgentArtifactQueryDto query) {
        long current = query.getCurrent() == null || query.getCurrent() < 1 ? 1 : query.getCurrent();
        long pageSize = query.getPageSize() == null ? 24 : Math.min(Math.max(query.getPageSize(), 1), 100);
        boolean recycled = Boolean.TRUE.equals(query.getRecycled());
        String extension = StringUtils.isNotBlank(query.getExtension()) ? normalizedExtension(query.getExtension()) : null;
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentArtifact> ownedQuery = Wrappers.lambdaQuery(AgentArtifact.class)
                .eq(AgentArtifact::getUserId, userId)
                .eq(StringUtils.isNotBlank(query.getAgentDefinitionId()), AgentArtifact::getAgentDefinitionId, query.getAgentDefinitionId())
                .like(StringUtils.isNotBlank(query.getFileName()), AgentArtifact::getFileName, StringUtils.trim(query.getFileName()))
                .like(StringUtils.isNotBlank(extension), AgentArtifact::getFileName, extension)
                .ge(query.getStartTime() != null, AgentArtifact::getCreatedAt, query.getStartTime())
                .le(query.getEndTime() != null, AgentArtifact::getCreatedAt, query.getEndTime())
                .isNotNull(recycled, AgentArtifact::getRecycledAt)
                .isNull(!recycled, AgentArtifact::getRecycledAt);
        applyTenant(ownedQuery);
        Page<AgentArtifact> page = page(new Page<>(current, pageSize), ownedQuery.orderByDesc(AgentArtifact::getCreatedAt));
        Map<String, String> agentNames = agentNames(page.getRecords());
        Page<AgentArtifactVo> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(item -> toVo(item, agentNames.get(item.getAgentDefinitionId())))
                .collect(Collectors.toList()));
        return result;
    }

    /**
     * 处理requireOwned。
     */
    @Override
    public AgentArtifact requireOwned(String id, String userId, boolean recycled) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentArtifact> ownedQuery = Wrappers.lambdaQuery(AgentArtifact.class)
                .eq(AgentArtifact::getId, id).eq(AgentArtifact::getUserId, userId)
                .isNotNull(recycled, AgentArtifact::getRecycledAt)
                .isNull(!recycled, AgentArtifact::getRecycledAt);
        applyTenant(ownedQuery);
        AgentArtifact artifact = getOne(ownedQuery);
        if (artifact == null || (artifact.getExpiresAt() != null && artifact.getExpiresAt() <= System.currentTimeMillis()))
            throw new ServerException(404, I18nUtils.getMessage("agent.artifact.not-found"));
        return artifact;
    }

    private void applyTenant(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentArtifact> query) {
        if (CurrentUser.getUser() != null && StringUtils.isNotBlank(CurrentUser.getUser().get("tenantId"))) {
            query.eq(AgentArtifact::getTenantId, CurrentUser.getUser().get("tenantId"));
        }
    }

    /**
     * 处理recycle。
     */
    @Override
    public void recycle(String id, String userId) {
        AgentArtifact artifact = requireOwned(id, userId, false);
        artifact.setRecycledAt(System.currentTimeMillis());
        updateById(artifact);
    }

    /**
     * 处理restore。
     */
    @Override
    public void restore(String id, String userId) {
        AgentArtifact artifact = requireOwned(id, userId, true);
        artifact.setRecycledAt(null);
        updateById(artifact);
    }

    /**
     * 处理purgeExpiredRecycled。
     */
    @Override
    public void purgeExpiredRecycled() {
        long cutoff = System.currentTimeMillis() - RECYCLE_RETENTION_MILLIS;
        List<AgentArtifact> artifacts = list(Wrappers.lambdaQuery(AgentArtifact.class)
                .isNotNull(AgentArtifact::getRecycledAt).le(AgentArtifact::getRecycledAt, cutoff));
        for (AgentArtifact artifact : artifacts) {
            try {
                objectStorageService.removeObject(artifactBucket, artifact.getObjectKey());
                removeById(artifact.getId());
            } catch (Exception e) {
                // Leave the record recoverable until storage cleanup succeeds on a subsequent run.
                log.warn("生成文件回收站清理失败: artifactId={}", artifact.getId(), e);
            }
        }
    }

    /**
     * 处理purgeExpiredArtifacts。
     */
    @Override
    public void purgeExpiredArtifacts() {
        long now = System.currentTimeMillis();
        List<AgentArtifact> artifacts = list(Wrappers.lambdaQuery(AgentArtifact.class)
                .isNotNull(AgentArtifact::getExpiresAt).le(AgentArtifact::getExpiresAt, now));
        for (AgentArtifact artifact : artifacts) {
            try {
                objectStorageService.removeObject(artifactBucket, artifact.getObjectKey());
                removeById(artifact.getId());
            } catch (Exception e) {
                log.warn("生成文件到期清理失败: artifactId={}", artifact.getId(), e);
            }
        }
    }

    /**
     * 处理normalizedExtension。
     */
    private String normalizedExtension(String extension) {
        String normalized = StringUtils.lowerCase(StringUtils.trim(extension));
        if (!normalized.startsWith(".")) normalized = "." + normalized;
        return normalized;
    }

    /**
     * 智能体Names。
     */
    private Map<String, String> agentNames(List<AgentArtifact> artifacts) {
        Set<String> ids = artifacts.stream().map(AgentArtifact::getAgentDefinitionId)
                .filter(StringUtils::isNotBlank).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        return agentDefinitionService.listByIds(ids).stream()
                .collect(Collectors.toMap(AgentDefinition::getId, AgentDefinition::getName, (left, right) -> left));
    }

    /**
     * 处理toVO。
     */
    private AgentArtifactVo toVo(AgentArtifact artifact, String agentName) {
        AgentArtifactVo vo = new AgentArtifactVo();
        BeanUtils.copyProperties(artifact, vo);
        vo.setAgentDefinitionName(agentName);
        if (artifact.getRecycledAt() != null)
            vo.setRecycleExpiresAt(artifact.getRecycledAt() + RECYCLE_RETENTION_MILLIS);
        return vo;
    }
}
