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
 * <p>内置交互工具（ask_user 等）、Skill 声明工具、统一工作流工具与 generate_artifact 始终保留；
 * 其余工具按关键字匹配（名称/编码/MCP 工具名）与 query embedding 语义召回排序，
 * 不足 topK 的部分按候选原顺序补齐。仅当非常驻候选超过 topK 时才真正丢弃工具。
 * embedding 未配置时关键字通道仍然生效。</p>
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
     * <p>只有非常驻候选超过 topK 时才裁剪；命中只决定顺序，不决定去留——否则一次偶然的
     * 关键字命中就会把其余已绑定工具挤出本轮工具集，而系统提示的能力目录仍然列着它们。</p>
     *
     * @param candidates       当前 Agent 可用工具（绑定或 Skill 收敛后）
     * @param protectedToolIds 必须常驻的工具 id（内置交互、Skill 声明工具、generate_artifact）
     * @param query            当前用户问题；为空时不裁剪
     * @return 常驻工具 + 至多 topK 个非常驻工具；候选不超上限时即全部候选
     */
    public List<AgentTool> route(List<AgentTool> candidates, Set<String> protectedToolIds, String query) {
        if (candidates == null || candidates.isEmpty() || StringUtils.isBlank(query)) {
            return candidates;
        }
        String embeddingModelId = routingConfigService.embeddingModelId();
        int topK = Math.max(1, routingConfigService.topK());
        List<AgentTool> routable = new ArrayList<>();
        for (AgentTool tool : candidates) {
            if (tool == null || tool.getId() == null) continue;
            if (isProtected(tool, protectedToolIds)) continue;
            routable.add(tool);
        }
        // 候选不超过召回上限时，裁剪省不下上下文，却会让已声明的能力从本轮工具集中消失。
        if (routable.size() <= topK) {
            return candidates;
        }
        String cacheKey = cacheKey(query, routable, embeddingModelId, topK);
        CachedRoute cached = routeCache.get(cacheKey);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            return merge(candidates, protectedToolIds, cached.toolIds);
        }
        List<String> selectedIds = select(query, routable, embeddingModelId, topK);
        evictRouteCache();
        routeCache.put(cacheKey, new CachedRoute(selectedIds, System.currentTimeMillis() + ROUTE_CACHE_TTL_MS));
        return merge(candidates, protectedToolIds, selectedIds);
    }
    private boolean isProtected(AgentTool tool, Set<String> protectedToolIds) {
        if (protectedToolIds != null && tool.getId() != null && protectedToolIds.contains(tool.getId())) {
            return true;
        }
        // 工作流生命周期工具是统一协议，必须始终可见，不能因用户问题与工具名不相似而丢失。
        return ARTIFACT_TOOL.equals(tool.getMcpToolName()) || EMAIL_TOOL.equals(tool.getMcpToolName())
                || "internal".equalsIgnoreCase(tool.getType())
                || "workflow".equalsIgnoreCase(tool.getType())
                || "workflow".equalsIgnoreCase(tool.getToolType())
                || Boolean.TRUE.equals(tool.getResident());
    }

    /**
     * 关键字命中与向量召回按优先级排在前，其余按候选原顺序补齐到 topK。
     *
     * <p>返回集合只受 topK 约束，不会再因“命中为空”而整体退化为忽略 query 的兜底；
     * 调用方保证候选数已超过 topK，故补齐后恰为 topK 个。</p>
     */
    private List<String> select(String query, List<AgentTool> routable, String embeddingModelId, int topK) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        String lowerQuery = query.toLowerCase();
        for (AgentTool tool : routable) {
            if (selected.size() >= topK) break;
            if (matchesKeyword(lowerQuery, tool)) selected.add(tool.getId());
        }
        if (StringUtils.isNotBlank(embeddingModelId) && selected.size() < topK) {
            try {
                ModelProvider provider = modelCatalogService.resolveProvider(embeddingModelId, "EMBEDDING");
                String vector = embeddingService.toVectorLiteral(embeddingService.embed(provider, query));
                List<String> toolIds = routable.stream().map(AgentTool::getId).collect(Collectors.toList());
                List<AgentToolRoutingIndex> hits = indexMapper.findSimilar(toolIds, vector, Math.min(topK, routable.size()));
                for (AgentToolRoutingIndex hit : hits) {
                    if (selected.size() >= topK) break;
                    if (hit.getVectorScore() != null && hit.getVectorScore() >= MIN_VECTOR_SCORE) selected.add(hit.getToolId());
                }
            } catch (Exception e) {
                log.debug("工具路由向量召回失败: {}", e.toString());
            }
        }
        // 补齐：命中只决定顺序，不决定去留。
        for (AgentTool tool : routable) {
            if (selected.size() >= topK) break;
            selected.add(tool.getId());
        }
        return new ArrayList<>(selected);
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

    private String cacheKey(String query, List<AgentTool> routable, String embeddingModelId, int topK) {
        String tenantId = CurrentUser.getUser() == null ? "" : CurrentUser.getUser().get("tenantId");
        String ids = routable.stream().map(AgentTool::getId).sorted().collect(Collectors.joining(","));
        return tenantId + '|' + embeddingModelId + '|' + topK + '|' + query.trim().replaceAll("\\s+", " ").toLowerCase() + '|' + ids;
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
