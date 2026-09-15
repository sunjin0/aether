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
import java.util.List;
import java.util.stream.Collectors;

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

    /** 命名与 id 对齐的候选，便于按名字构造关键字命中。 */
    private AgentTool named(String id) {
        return tool(id, id, id);
    }

    private List<String> idsOf(List<AgentTool> tools) {
        return tools.stream().map(AgentTool::getId).collect(Collectors.toList());
    }

    /** 让向量通道可用：任意查询都返回给定命中。 */
    private void stubVectorRecall(List<AgentToolRoutingIndex> hits) {
        when(routingConfigService.embeddingModelId()).thenReturn("emb-1");
        when(modelCatalogService.resolveProvider("emb-1", "EMBEDDING")).thenReturn(new ModelProvider());
        when(embeddingService.embed(any(ModelProvider.class), any(String.class))).thenReturn(Arrays.asList(0.1, 0.2));
        when(embeddingService.toVectorLiteral(any(List.class))).thenReturn("[0.1,0.2]");
        when(indexMapper.findSimilar(any(List.class), eq("[0.1,0.2]"), anyInt())).thenReturn(hits);
    }

    private AgentToolRoutingIndex hit(String toolId, double score) {
        AgentToolRoutingIndex hit = new AgentToolRoutingIndex();
        hit.setToolId(toolId);
        hit.setVectorScore(score);
        return hit;
    }

    @Test
    void returnsFullSetWhenQueryIsBlank() {
        ToolRouterService service = new ToolRouterService(indexMapper, embeddingService, modelCatalogService, routingConfigService);
        List<AgentTool> candidates = Arrays.asList(tool("t1", "search", "search"), tool("t2", "http", "http"));
        assertSame(candidates, service.route(candidates, Collections.<String>emptySet(), "  "));
    }

    /**
     * 回归：绑定工具数不超过 topK 时，一次偶然的关键字命中不得把其余工具挤出本轮工具集。
     * 系统提示的能力目录仍然列着它们，模型拿不到定义就会误判为“没有这个能力”。
     */
    @Test
    void keepsEveryBoundToolWhenCandidatesWithinTopK() {
        when(routingConfigService.embeddingModelId()).thenReturn(null);
        when(routingConfigService.topK()).thenReturn(8);
        ToolRouterService service = new ToolRouterService(indexMapper, embeddingService, modelCatalogService, routingConfigService);
        List<AgentTool> candidates = Arrays.asList(named("t1"), named("t2"), named("t3"));

        List<AgentTool> routed = service.route(candidates, Collections.<String>emptySet(), "随便说点什么");

        assertEquals(3, routed.size());
    }

    @Test
    void keywordMatchDoesNotDropOtherBoundTools() {
        when(routingConfigService.embeddingModelId()).thenReturn(null);
        when(routingConfigService.topK()).thenReturn(8);
        ToolRouterService service = new ToolRouterService(indexMapper, embeddingService, modelCatalogService, routingConfigService);
        List<AgentTool> candidates = Arrays.asList(named("search"), named("http"));

        List<AgentTool> routed = service.route(candidates, Collections.<String>emptySet(), "请用 search 工具");

        assertEquals(Arrays.asList("search", "http"), idsOf(routed));
    }

    /**
     * 候选超过 topK 时才裁剪：命中项（关键字 / 向量）优先保留，剩余名额按原顺序补齐。
     */
    @Test
    void trimsToTopKKeepingHitsWhenCandidatesExceedTopK() {
        when(routingConfigService.topK()).thenReturn(3);
        stubVectorRecall(Collections.singletonList(hit("t6", 0.85D)));
        ToolRouterService service = new ToolRouterService(indexMapper, embeddingService, modelCatalogService, routingConfigService);
        List<AgentTool> candidates = Arrays.asList(named("t1"), named("t2"), named("t3"), named("t4"), named("t5"), named("t6"));

        // 关键字只命中排在末位的 t5，向量召回末位的 t6，两者都必须在裁剪中活下来
        List<AgentTool> routed = service.route(candidates, Collections.<String>emptySet(), "只有 t5 这个词");

        List<String> ids = idsOf(routed);
        assertEquals(3, ids.size());
        assertTrue(ids.contains("t5"), "关键字命中项应保留");
        assertTrue(ids.contains("t6"), "向量命中项应保留");
        assertTrue(ids.contains("t1"), "剩余名额按原顺序补齐");
    }

    @Test
    void trimsToLeadingCandidatesWhenNothingMatches() {
        when(routingConfigService.embeddingModelId()).thenReturn(null);
        when(routingConfigService.topK()).thenReturn(2);
        ToolRouterService service = new ToolRouterService(indexMapper, embeddingService, modelCatalogService, routingConfigService);
        List<AgentTool> candidates = Arrays.asList(named("t1"), named("t2"), named("t3"), named("t4"), named("t5"));

        List<AgentTool> routed = service.route(candidates, Collections.<String>emptySet(), "毫无关联的问题");

        assertEquals(Arrays.asList("t1", "t2"), idsOf(routed));
    }

    @Test
    void capsSelectionAtTopKWhenMoreHitsThanLimit() {
        when(routingConfigService.embeddingModelId()).thenReturn(null);
        when(routingConfigService.topK()).thenReturn(2);
        ToolRouterService service = new ToolRouterService(indexMapper, embeddingService, modelCatalogService, routingConfigService);
        List<AgentTool> candidates = Arrays.asList(named("alpha"), named("beta"), named("gamma"), named("delta"), named("epsilon"));

        List<AgentTool> routed = service.route(candidates, Collections.<String>emptySet(), "alpha beta gamma 都提到了");

        assertEquals(2, routed.size());
    }

    @Test
    void keepsProtectedToolsBeyondTopK() {
        when(routingConfigService.embeddingModelId()).thenReturn(null);
        when(routingConfigService.topK()).thenReturn(2);
        ToolRouterService service = new ToolRouterService(indexMapper, embeddingService, modelCatalogService, routingConfigService);
        AgentTool generateArtifact = tool("ga", "generate_artifact", "generate_artifact");
        List<AgentTool> candidates = Arrays.asList(named("t1"), named("t2"), named("t3"), named("t4"), named("t5"), generateArtifact);

        List<AgentTool> routed = service.route(candidates, Collections.<String>emptySet(), "毫无关联的问题");

        // 2 个非常驻名额 + 1 个常驻工具，常驻不受 topK 约束
        assertEquals(3, routed.size());
        assertTrue(idsOf(routed).contains("ga"));
    }

    @Test
    void keepsConfiguredResidentAndInternalToolsBeyondTopK() {
        when(routingConfigService.embeddingModelId()).thenReturn(null);
        when(routingConfigService.topK()).thenReturn(1);
        ToolRouterService service = new ToolRouterService(indexMapper, embeddingService, modelCatalogService, routingConfigService);
        AgentTool resident = named("resident");
        resident.setResident(Boolean.TRUE);
        AgentTool internal = named("ask_user");
        internal.setType("internal");
        List<AgentTool> routed = service.route(Arrays.asList(named("t1"), named("t2"), resident, internal),
                Collections.<String>emptySet(), "unrelated");

        assertEquals(3, routed.size());
        assertTrue(idsOf(routed).contains("resident"));
        assertTrue(idsOf(routed).contains("ask_user"));
    }

    /**
     * 低于阈值的向量命中不参与排序，仍按兜底顺序取候选。
     */
    @Test
    void ignoresVectorHitsBelowThreshold() {
        when(routingConfigService.topK()).thenReturn(2);
        stubVectorRecall(Collections.singletonList(hit("t5", 0.15D)));
        ToolRouterService service = new ToolRouterService(indexMapper, embeddingService, modelCatalogService, routingConfigService);
        List<AgentTool> candidates = Arrays.asList(named("t1"), named("t2"), named("t3"), named("t4"), named("t5"));

        List<AgentTool> routed = service.route(candidates, Collections.<String>emptySet(), "查询第五个");

        assertEquals(Arrays.asList("t1", "t2"), idsOf(routed));
    }

    @Test
    void fallsBackToFillerWhenRecallFails() {
        when(routingConfigService.embeddingModelId()).thenReturn("emb-1");
        when(routingConfigService.topK()).thenReturn(2);
        when(modelCatalogService.resolveProvider("emb-1", "EMBEDDING")).thenThrow(new RuntimeException("down"));
        ToolRouterService service = new ToolRouterService(indexMapper, embeddingService, modelCatalogService, routingConfigService);
        List<AgentTool> candidates = Arrays.asList(named("t1"), named("t2"), named("t3"), named("t4"), named("t5"));

        List<AgentTool> routed = service.route(candidates, Collections.<String>emptySet(), "查询订单");

        assertEquals(Arrays.asList("t1", "t2"), idsOf(routed));
    }

    @Test
    void fallsBackToFillerWhenRecallReturnsNothing() {
        when(routingConfigService.topK()).thenReturn(2);
        stubVectorRecall(Collections.<AgentToolRoutingIndex>emptyList());
        ToolRouterService service = new ToolRouterService(indexMapper, embeddingService, modelCatalogService, routingConfigService);
        List<AgentTool> candidates = Arrays.asList(named("t1"), named("t2"), named("t3"), named("t4"), named("t5"));

        List<AgentTool> routed = service.route(candidates, Collections.<String>emptySet(), "随便说点什么");

        assertEquals(Arrays.asList("t1", "t2"), idsOf(routed));
    }
}
