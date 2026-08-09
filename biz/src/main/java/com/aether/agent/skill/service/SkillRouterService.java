package com.aether.agent.skill.service;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatMessage;
import com.aether.agent.model.ModelChatRequest;
import com.aether.agent.model.ModelChatResponse;
import com.aether.agent.model.ModelClientFactory;
import com.aether.agent.service.ModelProviderService;
import com.aether.agent.skill.entity.AgentDefinitionSkillBinding;
import com.aether.agent.skill.entity.AgentSkill;
import com.aether.agent.skill.entity.AgentSkillRoutingIndex;
import com.aether.agent.skill.entity.AgentSkillVersion;
import com.aether.agent.skill.mapper.AgentSkillRoutingIndexMapper;
import com.aether.agent.skill.service.impl.AgentSkillVersionServiceImpl;
import com.aether.knowledge.service.KnowledgeEmbeddingService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Claude-style progressive disclosure: only metadata is routed, never full Skill bodies. */
@Service
public class SkillRouterService {
    private static final int MAX_CANDIDATES = 12;
    private final AgentSkillService skillService;
    private final AgentSkillVersionServiceImpl versionService;
    private final AgentSkillRoutingIndexMapper indexMapper;
    private final KnowledgeEmbeddingService embeddingService;
    private final ModelProviderService modelProviderService;
    private final ModelClientFactory modelClientFactory;
    private final SkillRoutingConfigService routingConfigService;

    public SkillRouterService(AgentSkillService skillService, AgentSkillVersionServiceImpl versionService, AgentSkillRoutingIndexMapper indexMapper,
                              KnowledgeEmbeddingService embeddingService, ModelProviderService modelProviderService, ModelClientFactory modelClientFactory,
                              SkillRoutingConfigService routingConfigService) {
        this.skillService = skillService; this.versionService = versionService; this.indexMapper = indexMapper; this.embeddingService = embeddingService;
        this.modelProviderService = modelProviderService; this.modelClientFactory = modelClientFactory; this.routingConfigService = routingConfigService;
    }

    public SkillRouteDecision route(AgentDefinition agent, ModelProvider chatProvider, String query, List<AgentDefinitionSkillBinding> bindings) {
        SkillRouteDecision decision = new SkillRouteDecision();
        if (StringUtils.isBlank(query) || bindings == null || bindings.isEmpty()) { decision.setReason("NO_QUERY_OR_INSTALLATION"); return decision; }
        List<Candidate> available = bindings.stream().map(this::candidate).filter(c -> c != null).collect(Collectors.toList());
        LinkedHashSet<String> candidateIds = new LinkedHashSet<>();
        for (Candidate item : available) if (!matchesAny(query, item.excludeTerms) && matchesAny(query, item.triggerTerms)) { item.ruleMatched = true; candidateIds.add(item.version.getId()); }
        addSemanticCandidates(query, available, candidateIds);
        if (candidateIds.isEmpty()) { decision.setReason("NO_CANDIDATE"); return decision; }
        List<Candidate> candidates = available.stream().filter(c -> candidateIds.contains(c.version.getId())).sorted(Comparator.comparing((Candidate c) -> !c.ruleMatched).thenComparing(c -> c.priority == null ? Integer.MAX_VALUE : c.priority).thenComparing(c -> c.vectorScore == null ? Double.NEGATIVE_INFINITY : -c.vectorScore)).limit(MAX_CANDIDATES).collect(Collectors.toList());
        for (Candidate item : candidates) decision.getCandidates().add(item.audit());
        return classify(agent, chatProvider, query, candidates, decision);
    }

    private Candidate candidate(AgentDefinitionSkillBinding binding) {
        AgentSkill skill = skillService.getById(binding.getSkillId()); AgentSkillVersion version = versionService.getById(binding.getSkillVersionId());
        if (skill == null || version == null || !Integer.valueOf(1).equals(skill.getStatus()) || !Integer.valueOf(1).equals(version.getStatus()) || StringUtils.isBlank(version.getRoutingSummary())) return null;
        return new Candidate(skill, version, parse(version.getTriggerTerms()), parse(version.getExcludeTerms()), parse(version.getRoutingExamples()), binding.getPriority());
    }
    private void addSemanticCandidates(String query, List<Candidate> available, Set<String> output) {
        String embeddingProviderId = routingConfigService.embeddingProviderId();
        if (StringUtils.isBlank(embeddingProviderId) || available.isEmpty()) return;
        try {
            ModelProvider provider = modelProviderService.getById(embeddingProviderId); if (provider == null || !Integer.valueOf(1).equals(provider.getStatus())) return;
            String vector = embeddingService.toVectorLiteral(embeddingService.embed(provider, query));
            List<AgentSkillRoutingIndex> hits = indexMapper.findSimilar(available.stream().map(c -> c.version.getId()).collect(Collectors.toList()), vector, MAX_CANDIDATES);
            Map<String, Candidate> byVersion = available.stream().collect(Collectors.toMap(c -> c.version.getId(), c -> c));
            for (AgentSkillRoutingIndex hit : hits) { Candidate candidate = byVersion.get(hit.getSkillVersionId()); if (candidate != null && !matchesAny(query, candidate.excludeTerms)) { candidate.vectorScore = hit.getVectorScore(); output.add(hit.getSkillVersionId()); } }
        } catch (Exception ignored) { /* semantic routing is an optional recall source */ }
    }
    private SkillRouteDecision classify(AgentDefinition agent, ModelProvider provider, String query, List<Candidate> candidates, SkillRouteDecision decision) {
        try {
            ModelChatRequest request = new ModelChatRequest(); request.setAgent(agent); request.setProvider(provider); request.setTemperature(BigDecimal.ZERO); request.setMaxCompletionTokens(80); request.setResponseFormat(Collections.<String, Object>singletonMap("type", "json_object"));
            request.setMessages(Collections.singletonList(new ModelChatMessage("user", prompt(query, candidates))));
            ModelChatResponse response = modelClientFactory.getClient(provider).chat(request); JSONObject json = JSON.parseObject(response == null ? null : response.getContent());
            String id = json == null ? null : json.getString("skillVersionId"); Double confidence = json == null ? null : json.getDouble("confidence");
            if ("NONE".equalsIgnoreCase(id) || confidence == null || confidence < 0.60 || candidates.stream().noneMatch(c -> c.version.getId().equals(id))) { decision.setReason("LOW_CONFIDENCE_OR_NONE"); decision.setConfidence(confidence); return decision; }
            decision.setSkillVersionId(id); decision.setConfidence(confidence); decision.setReason(StringUtils.abbreviate(json.getString("reason"), 200)); return decision;
        } catch (Exception e) { decision.setReason("ROUTER_UNAVAILABLE"); return decision; }
    }
    private String prompt(String query, List<Candidate> candidates) { StringBuilder out = new StringBuilder("Select exactly one relevant Skill or NONE. Return JSON only: {\"skillVersionId\":\"id|NONE\",\"confidence\":0..1,\"reason\":\"short\"}. Do not answer the user.\nUser task:\n").append(query).append("\nCandidates:\n"); for (Candidate c : candidates) out.append(JSON.toJSONString(c.metadata())).append('\n'); return out.toString(); }
    private boolean matchesAny(String query, List<String> terms) { String source = query.toLowerCase(); for (String term : terms) if (StringUtils.isNotBlank(term) && source.contains(term.toLowerCase())) return true; return false; }
    private List<String> parse(String text) { try { List<String> result = JSON.parseArray(StringUtils.defaultIfBlank(text, "[]"), String.class); return result == null ? Collections.<String>emptyList() : result; } catch (Exception e) { return Collections.emptyList(); } }
    private static class Candidate { final AgentSkill skill; final AgentSkillVersion version; final List<String> triggerTerms, excludeTerms, examples; final Integer priority; boolean ruleMatched; Double vectorScore; Candidate(AgentSkill s, AgentSkillVersion v, List<String> t, List<String> e, List<String> x, Integer p) { skill=s;version=v;triggerTerms=t;excludeTerms=e;examples=x;priority=p; } Map<String,Object> metadata(){ Map<String,Object> r=new LinkedHashMap<>();r.put("skillVersionId",version.getId());r.put("name",skill.getName());r.put("summary",version.getRoutingSummary());r.put("category",skill.getCategory());r.put("tags",skill.getTags());r.put("examples",examples);return r;} Map<String,Object> audit(){Map<String,Object> r=metadata();r.put("ruleMatched",ruleMatched);r.put("vectorScore",vectorScore);r.put("priority",priority);return r;} }
}
