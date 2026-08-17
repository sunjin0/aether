package com.aether.agent.skill.service;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatRequest;
import com.aether.agent.model.ModelChatResponse;
import com.aether.agent.model.ModelClient;
import com.aether.agent.model.ModelClientFactory;
import com.aether.agent.service.ModelCatalogService;
import com.aether.agent.skill.entity.AgentDefinitionSkillBinding;
import com.aether.agent.skill.entity.AgentSkill;
import com.aether.agent.skill.entity.AgentSkillVersion;
import com.aether.agent.skill.mapper.AgentSkillRoutingIndexMapper;
import com.aether.agent.skill.service.impl.AgentSkillVersionServiceImpl;
import com.aether.knowledge.service.KnowledgeEmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证SkillRouter服务的行为。
 */
class SkillRouterServiceTest {
    private final AgentSkillService skillService = mock(AgentSkillService.class);
    private final AgentSkillVersionServiceImpl versionService = mock(AgentSkillVersionServiceImpl.class);
    private final AgentSkillRoutingIndexMapper indexMapper = mock(AgentSkillRoutingIndexMapper.class);
    private final KnowledgeEmbeddingService embeddingService = mock(KnowledgeEmbeddingService.class);
    private final ModelCatalogService modelCatalogService = mock(ModelCatalogService.class);
    private final ModelClientFactory clientFactory = mock(ModelClientFactory.class);
    private final ModelClient client = mock(ModelClient.class);
    private final SkillRoutingConfigService routingConfigService = mock(SkillRoutingConfigService.class);
    private final SkillRouterService service = new SkillRouterService(skillService, versionService, indexMapper, embeddingService, modelCatalogService, clientFactory, routingConfigService);
    private final AgentDefinition agent = new AgentDefinition();
    private final ModelProvider provider = new ModelProvider();

    /**
     * 处理setup。
     */
    @BeforeEach
    void setup() {
        agent.setId("a1");
        provider.setId("p1");
        when(routingConfigService.embeddingModelId()).thenReturn("");
        AgentSkill skill = new AgentSkill();
        skill.setId("s1");
        skill.setName("Refund");
        skill.setCategory("support");
        skill.setStatus(1);
        AgentSkillVersion version = new AgentSkillVersion();
        version.setId("v1");
        version.setSkillId("s1");
        version.setStatus(1);
        version.setRoutingSummary("Handle refund requests");
        version.setTriggerTerms("[\"refund\"]");
        version.setExcludeTerms("[\"security\"]");
        version.setRoutingExamples("[\"How do I request a refund?\"]");
        when(skillService.getById("s1")).thenReturn(skill);
        when(versionService.getById("v1")).thenReturn(version);
        when(clientFactory.getClient(provider)).thenReturn(client);
    }

    /**
     * 处理choosesRuleCandidateUsingOnlyMetadata。
     */
    @Test
    void choosesRuleCandidateUsingOnlyMetadata() {
        ModelChatResponse response = new ModelChatResponse();
        response.setContent("{\"skillVersionId\":\"v1\",\"confidence\":0.91,\"reason\":\"refund request\"}");
        when(client.chat(any(ModelChatRequest.class))).thenReturn(response);
        SkillRouteDecision result = service.route(agent, provider, "I need a refund", Collections.singletonList(binding()));
        assertEquals("v1", result.getSkillVersionId());
        assertEquals(1, result.getCandidates().size());
        assertEquals(Boolean.TRUE, result.getCandidates().get(0).get("ruleMatched"));
        verify(client).chat(any(ModelChatRequest.class));
    }

    /**
     * 处理exclusionPreventsCandidateAndDefaultsToNone。
     */
    @Test
    void exclusionPreventsCandidateAndDefaultsToNone() {
        SkillRouteDecision result = service.route(agent, provider, "refund for security product", Collections.singletonList(binding()));
        assertFalse(result.isMatched());
        assertEquals("NO_CANDIDATE", result.getReason());
    }

    /**
     * 处理invalidJsonAndLowConfidenceDefaultToNone。
     */
    @Test
    void invalidJsonAndLowConfidenceDefaultToNone() {
        ModelChatResponse invalid = new ModelChatResponse();
        invalid.setContent("not-json");
        when(client.chat(any(ModelChatRequest.class))).thenReturn(invalid);
        assertEquals("ROUTER_UNAVAILABLE", service.route(agent, provider, "refund", Collections.singletonList(binding())).getReason());
        ModelChatResponse low = new ModelChatResponse();
        low.setContent("{\"skillVersionId\":\"v1\",\"confidence\":0.2}");
        when(client.chat(any(ModelChatRequest.class))).thenReturn(low);
        assertEquals("LOW_CONFIDENCE_OR_NONE", service.route(agent, provider, "refund", Collections.singletonList(binding())).getReason());
    }

    /**
     * 处理binding。
     */
    private AgentDefinitionSkillBinding binding() {
        AgentDefinitionSkillBinding binding = new AgentDefinitionSkillBinding();
        binding.setSkillId("s1");
        binding.setSkillVersionId("v1");
        binding.setStatus(1);
        binding.setPriority(1);
        return binding;
    }
}
