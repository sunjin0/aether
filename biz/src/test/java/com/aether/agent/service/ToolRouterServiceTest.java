package com.aether.agent.service;

import com.aether.agent.entity.AgentTool;
import com.aether.agent.entity.AgentToolRoutingIndex;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.mapper.AgentToolRoutingIndexMapper;
import com.aether.knowledge.service.KnowledgeEmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolRouterServiceTest {

    @Mock
    private AgentToolRoutingIndexMapper indexMapper;
    @Mock
    private KnowledgeEmbeddingService embeddingService;
    @Mock
    private ModelCatalogService modelCatalogService;
    @Mock
    private ToolRoutingConfigService routingConfigService;

    private AgentTool tool(String id, String name, String mcpToolName) {
        AgentTool tool = new AgentTool();
        tool.setId(id);
        tool.setName(name);
        tool.setMcpToolName(mcpToolName);
        return tool;
    }

    @Test
    void returnsFullSetWhenQueryIsBlank() {
        ToolRouterService service = new ToolRouterService(indexMapper, embeddingService, modelCatalogService, routingConfigService);
        List<AgentTool> candidates = Arrays.asList(tool("t1", "search", "search"), tool("t2", "http", "http"));
        assertSame(candidates, service.route(candidates, Collections.<String>emptySet(), "  "));
    }

    @Test
    void returnsEmptyWhenEmbeddingNotConfiguredAndNoKeywordMatch() {
        when(routingConfigService.embeddingModelId()).thenReturn(null);
        ToolRouterService service = new ToolRouterService(indexMapper, embeddingService, modelCatalogService, routingConfigService);
        List<AgentTool> candidates = Arrays.asList(tool("t1", "search", "search"), tool("t2", "http", "http"));
        List<AgentTool> routed = service.route(candidates, Collections.<String>emptySet(), "查找订单");

        assertTrue(routed.isEmpty());
    }

    @Test
    void keywordMatchRoutesToolWithoutEmbedding() {
        when(routingConfigService.embeddingModelId()).thenReturn(null);
        ToolRouterService service = new ToolRouterService(indexMapper, embeddingService, modelCatalogService, routingConfigService);
        AgentTool search = tool("t1", "search", "search");
        AgentTool http = tool("t2", "http", "http");
        List<AgentTool> candidates = Arrays.asList(search, http);
        List<AgentTool> routed = service.route(candidates, Collections.<String>emptySet(), "请用 search 工具");

        assertEquals(1, routed.size());
        assertEquals("t1", routed.get(0).getId());
    }

    @Test
    void keywordMatchCombinedWithEmbedding() {
        when(routingConfigService.embeddingModelId()).thenReturn("emb-1");
        when(routingConfigService.topK()).thenReturn(8);
        when(modelCatalogService.resolveProvider("emb-1", "EMBEDDING")).thenReturn(new ModelProvider());
        when(embeddingService.embed(any(ModelProvider.class), any(String.class))).thenReturn(Arrays.asList(0.1, 0.2));
        when(embeddingService.toVectorLiteral(any(List.class))).thenReturn("[0.1,0.2]");
        AgentToolRoutingIndex hit = new AgentToolRoutingIndex();
        hit.setToolId("t2");
        hit.setVectorScore(0.85D);
        when(indexMapper.findSimilar(any(List.class), eq("[0.1,0.2]"), anyInt())).thenReturn(Collections.singletonList(hit));

        ToolRouterService service = new ToolRouterService(indexMapper, embeddingService, modelCatalogService, routingConfigService);
        AgentTool search = tool("t1", "search", "search");
        AgentTool http = tool("t2", "http", "http");
        AgentTool db = tool("t3", "db", "db");
        List<AgentTool> candidates = Arrays.asList(search, http, db);
        List<AgentTool> routed = service.route(candidates, Collections.<String>emptySet(), "请用 search 查订单");

        assertEquals(2, routed.size());
        assertTrue(routed.stream().anyMatch(c -> "t1".equals(c.getId())));
        assertTrue(routed.stream().anyMatch(c -> "t2".equals(c.getId())));
    }

    @Test
    void keepsProtectedAndArtifactToolsAndRecallsTopK() {
        when(routingConfigService.embeddingModelId()).thenReturn("emb-1");
        when(routingConfigService.topK()).thenReturn(8);
        when(modelCatalogService.resolveProvider("emb-1", "EMBEDDING")).thenReturn(new ModelProvider());
        when(embeddingService.embed(any(ModelProvider.class), any(String.class))).thenReturn(Arrays.asList(0.1, 0.2));
        when(embeddingService.toVectorLiteral(any(List.class))).thenReturn("[0.1,0.2]");
        AgentToolRoutingIndex hit = new AgentToolRoutingIndex();
        hit.setToolId("t2");
        hit.setVectorScore(0.82D);
        when(indexMapper.findSimilar(eq(Arrays.asList("t1", "t2", "t3")), eq("[0.1,0.2]"), anyInt())).thenReturn(Collections.singletonList(hit));

        ToolRouterService service = new ToolRouterService(indexMapper, embeddingService, modelCatalogService, routingConfigService);
        AgentTool generateArtifact = tool("ga", "generate_artifact", "generate_artifact");
        AgentTool search = tool("t1", "search", "search");
        AgentTool http = tool("t2", "http", "http");
        AgentTool db = tool("t3", "db", "db");
        List<AgentTool> candidates = Arrays.asList(search, http, db, generateArtifact);
        List<AgentTool> routed = service.route(candidates, new HashSet<>(Arrays.asList("ask_user")), "查询数据库中的订单");

        List<String> ids = Arrays.asList(routed.get(0).getId(), routed.get(1).getId());
        // 召回的 t2 + 常驻的 generate_artifact（其 id 不参与路由）保留
        assertTrue(ids.contains("t2"));
        assertTrue(ids.contains("ga"));
        assertEquals(2, routed.size());
    }

    @Test
    void filtersVectorHitsBelowThreshold() {
        when(routingConfigService.embeddingModelId()).thenReturn("emb-1");
        when(routingConfigService.topK()).thenReturn(8);
        when(modelCatalogService.resolveProvider("emb-1", "EMBEDDING")).thenReturn(new ModelProvider());
        when(embeddingService.embed(any(ModelProvider.class), any(String.class))).thenReturn(Arrays.asList(0.1, 0.2));
        when(embeddingService.toVectorLiteral(any(List.class))).thenReturn("[0.1,0.2]");
        AgentToolRoutingIndex lowHit = new AgentToolRoutingIndex();
        lowHit.setToolId("t1");
        lowHit.setVectorScore(0.15D);
        AgentToolRoutingIndex highHit = new AgentToolRoutingIndex();
        highHit.setToolId("t3");
        highHit.setVectorScore(0.60D);
        when(indexMapper.findSimilar(any(List.class), eq("[0.1,0.2]"), anyInt())).thenReturn(Arrays.asList(lowHit, highHit));

        ToolRouterService service = new ToolRouterService(indexMapper, embeddingService, modelCatalogService, routingConfigService);
        List<AgentTool> candidates = Arrays.asList(tool("t1", "search", "search"), tool("t2", "http", "http"), tool("t3", "db", "db"));
        List<AgentTool> routed = service.route(candidates, Collections.<String>emptySet(), "查询数据库");

        assertEquals(1, routed.size());
        assertEquals("t3", routed.get(0).getId());
    }

    @Test
    void returnsProtectedOnlyWhenNoHits() {
        when(routingConfigService.embeddingModelId()).thenReturn("emb-1");
        when(modelCatalogService.resolveProvider("emb-1", "EMBEDDING")).thenReturn(new ModelProvider());
        when(embeddingService.embed(any(ModelProvider.class), any(String.class))).thenReturn(Arrays.asList(0.1, 0.2));
        when(embeddingService.toVectorLiteral(any(List.class))).thenReturn("[0.1,0.2]");
        when(indexMapper.findSimilar(any(List.class), eq("[0.1,0.2]"), anyInt())).thenReturn(Collections.emptyList());

        ToolRouterService service = new ToolRouterService(indexMapper, embeddingService, modelCatalogService, routingConfigService);
        List<AgentTool> candidates = Arrays.asList(tool("t1", "search", "search"), tool("t2", "http", "http"));
        List<AgentTool> routed = service.route(candidates, new HashSet<>(Arrays.asList("t1")), "随便说点什么");

        assertEquals(1, routed.size());
        assertEquals("t1", routed.get(0).getId());
    }

    @Test
    void returnsProtectedOnlyWhenRecallFails() {
        when(routingConfigService.embeddingModelId()).thenReturn("emb-1");
        when(modelCatalogService.resolveProvider("emb-1", "EMBEDDING")).thenThrow(new RuntimeException("down"));

        ToolRouterService service = new ToolRouterService(indexMapper, embeddingService, modelCatalogService, routingConfigService);
        List<AgentTool> candidates = Arrays.asList(tool("t1", "search", "search"), tool("t2", "http", "http"));
        List<AgentTool> routed = service.route(candidates, new HashSet<>(Arrays.asList("t2")), "查询订单");

        assertEquals(1, routed.size());
        assertEquals("t2", routed.get(0).getId());
    }
}