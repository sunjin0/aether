package com.aether.agent.service;

import com.aether.agent.entity.AgentTool;
import com.aether.agent.entity.AgentToolRoutingIndex;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.mapper.AgentToolRoutingIndexMapper;
import com.aether.knowledge.service.KnowledgeEmbeddingService;
import com.aether.local.CurrentUser;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 按当前 query 召回相关工具，裁剪无关工具定义以节省模型上下文。
 *
 * <p>内置交互工具（ask_user 等）、Skill required 工具与 generate_artifact 始终保留；
 * 其余工具按关键字匹配（名称/编码/MCP 工具名）与 query embedding 语义召回合并输出，
 * 未匹配的工具不会携带给模型。embedding 未配置时关键字通道仍然生效。</p>
 */
@Service
public class ToolRouterService {
    private static final Logger log = LoggerFactory.getLogger(ToolRouterService.class);
    private static final String ARTIFACT_TOOL = "generate_artifact";
    /**
     * 邮件发送是经审批的高风险外部操作，不能因中文/英文关键词或向量索引缺失而从模型工具集中裁掉。
     */
    private static final String EMAIL_TOOL = "send_email";
    private static final double MIN_VECTOR_SCORE = 0.30D;

    private final AgentToolRoutingIndexMapper indexMapper;
    private final KnowledgeEmbeddingService embeddingService;
    private final ModelCatalogService modelCatalogService;
    private final ToolRoutingConfigService routingConfigService;

    private final ConcurrentHashMap<String, CachedRoute> routeCache = new ConcurrentHashMap<>();
    private static final long ROUTE_CACHE_TTL_MS = 60 * 1000L;
    private static final int ROUTE_CACHE_MAX_SIZE = 1000;

    public ToolRouterService(AgentToolRoutingIndexMapper indexMapper, KnowledgeEmbeddingService embeddingService,
                             ModelCatalogService modelCatalogService, ToolRoutingConfigService routingConfigService) {
        this.indexMapper = indexMapper;
        this.embeddingService = embeddingService;
        this.modelCatalogService = modelCatalogService;
        this.routingConfigService = routingConfigService;
    }

    /**
     * 从候选工具中选出应携带给模型的子集。
     *
     * @param candidates       当前 Agent 可用工具（绑定或 Skill 收敛后）
     * @param protectedToolIds 必须常驻的工具 id（内置交互、Skill required、generate_artifact）
     * @param query            当前用户问题；为空时不裁剪
     * @return 匹配到的工具与常驻工具子集；无命中时仅返回常驻工具
     */
    public List<AgentTool> route(List<AgentTool> candidates, Set<String> protectedToolIds, String query) {
        if (candidates == null || candidates.isEmpty() || StringUtils.isBlank(query)) {
            return candidates;
        }
        String embeddingModelId = routingConfigService.embeddingModelId();
        List<AgentTool> routable = new ArrayList<>();
        for (AgentTool tool : candidates) {
            if (tool == null || tool.getId() == null) continue;
            if (isProtected(tool, protectedToolIds)) continue;
            routable.add(tool);
        }
        if (routable.isEmpty()) {
            return candidates;
        }
        String cacheKey = cacheKey(query, routable, embeddingModelId);
        CachedRoute cached = routeCache.get(cacheKey);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            return merge(candidates, protectedToolIds, cached.toolIds);
        }
        List<String> selected = select(query, routable, embeddingModelId);
        // The permanent capability catalog may lead the model to an otherwise relevant
        // tool whose name was not present in the user wording.  A no-hit route therefore
        // falls back to the available definitions for this turn instead of making a
        // declared capability impossible to invoke.
        if (selected == null) {
            log.debug("工具路由无命中，回退完整定义: candidates={}", candidates.size());
            return candidates;
        }
        List<String> selectedIds = selected == null ? Collections.<String>emptyList() : selected;
        evictRouteCache();
        if (!selectedIds.isEmpty()) {
            routeCache.put(cacheKey, new CachedRoute(selectedIds, System.currentTimeMillis() + ROUTE_CACHE_TTL_MS));
        }
        return merge(candidates, protectedToolIds, selectedIds);
    }
    private boolean isProtected(AgentTool tool, Set<String> protectedToolIds) {
        if (protectedToolIds != null && tool.getId() != null && protectedToolIds.contains(tool.getId())) {
            return true;
        }
        return ARTIFACT_TOOL.equals(tool.getMcpToolName()) || EMAIL_TOOL.equals(tool.getMcpToolName());
    }

    /**
     * 关键字命中 + 向量召回合并；无任何命中时返回 null 表示回退全量。
     */
    private List<String> select(String query, List<AgentTool> routable, String embeddingModelId) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        String lowerQuery = query.toLowerCase();
        for (AgentTool tool : routable)
            if (matchesKeyword(lowerQuery, tool)) selected.add(tool.getId());
        if (StringUtils.isNotBlank(embeddingModelId)) {
            try {
                ModelProvider provider = modelCatalogService.resolveProvider(embeddingModelId, "EMBEDDING");
                String vector = embeddingService.toVectorLiteral(embeddingService.embed(provider, query));
                List<String> toolIds = routable.stream().map(AgentTool::getId).collect(Collectors.toList());
                int topK = Math.max(1, routingConfigService.topK());
                List<AgentToolRoutingIndex> hits = indexMapper.findSimilar(toolIds, vector, Math.min(topK, routable.size()));
                for (AgentToolRoutingIndex hit : hits)
                    if (hit.getVectorScore() != null && hit.getVectorScore() >= MIN_VECTOR_SCORE && selected.size() < topK) selected.add(hit.getToolId());
            } catch (Exception e) {
                log.debug("工具路由向量召回失败: {}", e.toString());
            }
        }
        if (!selected.isEmpty()) {
            return new ArrayList<>(selected);
        }
        // 无命中时仍需提供可用工具，但全量暴露会挤占模型上下文并增加误调用概率。
        // 因此保留确定性的有限兜底集合；受保护工具由调用方合并，不受此处影响。
        int fallbackSize = Math.min(Math.max(1, routingConfigService.topK()), routable.size());
        List<String> fallback = new ArrayList<>();
        for (int i = 0; i < fallbackSize; i++) {
            fallback.add(routable.get(i).getId());
        }
        return fallback;
    }

    /**
     * 判断关键字是否命中。
     */
    private boolean matchesKeyword(String lowerQuery, AgentTool tool) {
        return contains(lowerQuery, tool.getName()) || contains(lowerQuery, tool.getCode()) || contains(lowerQuery, tool.getMcpToolName());
    }

    /**
     * 处理contains。
     */
    private boolean contains(String lowerQuery, String text) {
        return StringUtils.isNotBlank(text) && lowerQuery.contains(text.toLowerCase());
    }

    /**
     * 常驻工具 + 召回的候选工具，按候选原顺序稳定输出。
     */
    private List<AgentTool> merge(List<AgentTool> candidates, Set<String> protectedToolIds, List<String> selectedIds) {
        Set<String> selected = new java.util.HashSet<>(selectedIds == null ? Collections.<String>emptyList() : selectedIds);
        List<AgentTool> result = new ArrayList<>();
        for (AgentTool tool : candidates) {
            if (tool == null || tool.getId() == null) continue;
            if (selected.contains(tool.getId()) || isProtected(tool, protectedToolIds)) {
                result.add(tool);
            }
        }
        return result;
    }

    private String cacheKey(String query, List<AgentTool> routable, String embeddingModelId) {
        String tenantId = CurrentUser.getUser() == null ? "" : CurrentUser.getUser().get("tenantId");
        String ids = routable.stream().map(AgentTool::getId).sorted().collect(Collectors.joining(","));
        return tenantId + '|' + embeddingModelId + '|' + query.trim().replaceAll("\\s+", " ").toLowerCase() + '|' + ids;
    }

    private void evictRouteCache() {
        if (routeCache.size() < ROUTE_CACHE_MAX_SIZE) return;
        long now = System.currentTimeMillis();
        for (java.util.Map.Entry<String, CachedRoute> entry : routeCache.entrySet()) {
            if (entry.getValue().expiresAt <= now) routeCache.remove(entry.getKey(), entry.getValue());
        }
        if (routeCache.size() >= ROUTE_CACHE_MAX_SIZE) routeCache.clear();
    }

    private static class CachedRoute {
        final List<String> toolIds;
        final long expiresAt;

        CachedRoute(List<String> toolIds, long expiresAt) {
            this.toolIds = toolIds;
            this.expiresAt = expiresAt;
        }
    }
}
