package com.aether.agent.service;

import com.aether.agent.entity.AgentTool;
import com.aether.agent.entity.AgentToolRoutingIndex;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.mapper.AgentToolMapper;
import com.aether.agent.mapper.AgentToolRoutingIndexMapper;
import com.aether.knowledge.service.KnowledgeEmbeddingService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 维护工具路由索引：工具新增/变更/删除后异步重建其 embedding 向量。
 */
@Service
public class ToolRoutingIndexService {
    private final AgentToolMapper toolMapper;
    private final AgentToolRoutingIndexMapper mapper;
    private final KnowledgeEmbeddingService embeddingService;
    private final ModelCatalogService modelCatalogService;
    private final ToolRoutingConfigService routingConfigService;
    private final TaskExecutor taskExecutor;

    public ToolRoutingIndexService(AgentToolMapper toolMapper, AgentToolRoutingIndexMapper mapper,
                                   KnowledgeEmbeddingService embeddingService, ModelCatalogService modelCatalogService,
                                   ToolRoutingConfigService routingConfigService,
                                   @Qualifier("asyncPoolTaskExecutor") TaskExecutor taskExecutor) {
        this.toolMapper = toolMapper;
        this.mapper = mapper;
        this.embeddingService = embeddingService;
        this.modelCatalogService = modelCatalogService;
        this.routingConfigService = routingConfigService;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 工具增删改后异步重建索引；embedding 未配置时直接跳过。
     */
    public void scheduleIndexTool(String toolId) {
        if (StringUtils.isBlank(toolId)) return;
        taskExecutor.execute(() -> indexTool(toolId));
    }

    /**
     * 配置变更后重建全部启用工具的索引。
     */
    public void reindexAllTools() {
        taskExecutor.execute(() -> {
            List<AgentTool> tools = toolMapper.selectList(Wrappers.lambdaQuery(AgentTool.class)
                    .eq(AgentTool::getDeleted, false).eq(AgentTool::getStatus, 1));
            for (AgentTool tool : tools) {
                indexTool(tool.getId());
            }
        });
    }

    /**
     * 重建单个工具的索引；工具不存在或停用时删除旧索引。
     */
    public void indexTool(String toolId) {
        AgentTool tool = toolMapper.selectById(toolId);
        if (tool == null || Boolean.TRUE.equals(tool.getDeleted()) || !Integer.valueOf(1).equals(tool.getStatus())) {
            removeIndex(toolId);
            return;
        }
        AgentToolRoutingIndex existing = mapper.selectOne(Wrappers.lambdaQuery(AgentToolRoutingIndex.class)
                .eq(AgentToolRoutingIndex::getToolId, toolId));
        String content = buildContent(tool);
        String contentHash = sha256(content);
        if (existing != null && contentHash.equals(existing.getContentHash()) && Integer.valueOf(1).equals(existing.getIndexStatus())) {
            return;
        }
        AgentToolRoutingIndex index = existing == null ? new AgentToolRoutingIndex() : existing;
        index.setToolId(toolId);
        index.setContentHash(contentHash);
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
     * 删除工具索引（逻辑删除）。
     */
    public void removeIndex(String toolId) {
        AgentToolRoutingIndex index = mapper.selectOne(Wrappers.lambdaQuery(AgentToolRoutingIndex.class)
                .eq(AgentToolRoutingIndex::getToolId, toolId));
        if (index == null) return;
        index.setDeleted(true);
        index.setUpdatedAt(System.currentTimeMillis());
        mapper.updateById(index);
    }

    /**
     * 构建用于向量化的工具描述内容。
     */
    private String buildContent(AgentTool tool) {
        StringBuilder content = new StringBuilder();
        if (StringUtils.isNotBlank(tool.getName())) content.append(tool.getName()).append('\n');
        if (StringUtils.isNotBlank(tool.getCode())) content.append(tool.getCode()).append('\n');
        if (StringUtils.isNotBlank(tool.getToolType())) content.append(tool.getToolType()).append('\n');
        if (StringUtils.isNotBlank(tool.getDescription())) content.append(tool.getDescription()).append('\n');
        if (StringUtils.isNotBlank(tool.getMcpToolName())) content.append(tool.getMcpToolName()).append('\n');
        return content.toString();
    }

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