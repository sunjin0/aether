package com.aether.agent.skill.service;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.model.ModelChatMessage;
import com.aether.agent.model.ModelChatRequest;
import com.aether.agent.model.ModelChatResponse;
import com.aether.agent.model.ModelClientFactory;
import com.aether.agent.observability.ChatLatencyMetrics;
import com.aether.agent.service.ModelCatalogService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Claude-style progressive disclosure: only metadata is routed, never full Skill bodies.
 */
@Service
public class SkillRouterService {
    private static final int MAX_CANDIDATES = 12;
    private static final long ROUTE_CACHE_TTL_MS = 60 * 1000L;
    private static final int ROUTE_CACHE_MAX_SIZE = 1000;
    private static final Logger log = LoggerFactory.getLogger(SkillRouterService.class);
    private final AgentSkillService skillService;
    private final AgentSkillVersionServiceImpl versionService;
    private final AgentSkillRoutingIndexMapper indexMapper;
    private final KnowledgeEmbeddingService embeddingService;
    private final ModelCatalogService modelCatalogService;
    private final ModelClientFactory modelClientFactory;
    private final SkillRoutingConfigService routingConfigService;
    /**
     * Routing is deterministic for one installed-version set and query during the short TTL.
     */
    private final ConcurrentHashMap<String, CachedRoute> routeCache = new ConcurrentHashMap<>();

    /**
     * 创建 {@code SkillRouterService} 实例。
     */
    public SkillRouterService(AgentSkillService skillService, AgentSkillVersionServiceImpl versionService, AgentSkillRoutingIndexMapper indexMapper,
                              KnowledgeEmbeddingService embeddingService, ModelCatalogService modelCatalogService, ModelClientFactory modelClientFactory,
                              SkillRoutingConfigService routingConfigService) {
        this.skillService = skillService;
        this.versionService = versionService;
        this.indexMapper = indexMapper;
        this.embeddingService = embeddingService;
        this.modelCatalogService = modelCatalogService;
        this.modelClientFactory = modelClientFactory;
        this.routingConfigService = routingConfigService;
    }

    /**
     * 处理route。
     */
    public SkillRouteDecision route(AgentDefinition agent, ModelProvider chatProvider, String query, List<AgentDefinitionSkillBinding> bindings) {
        SkillRouteDecision decision = new SkillRouteDecision();
        if (StringUtils.isBlank(query) || bindings == null || bindings.isEmpty()) {
            decision.setReason("NO_QUERY_OR_INSTALLATION");
            return decision;
        }
        String cacheKey = routeCacheKey(agent, query, bindings);
        CachedRoute cached = routeCache.get(cacheKey);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            ChatLatencyMetrics.record("chat.skill_route_cache_hit", 1L);
            log.debug("技能路由缓存命中: requestId={}, agentId={}", currentRequestId(), agent == null ? null : agent.getId());
            return copy(cached.decision);
        }
        long startedAt = System.currentTimeMillis();
        List<Candidate> available = bindings.stream().map(this::candidate).filter(c -> c != null).collect(Collectors.toList());
        LinkedHashSet<String> candidateIds = new LinkedHashSet<>();
        for (Candidate item : available)
            if (!matchesAny(query, item.excludeTerms) && matchesAny(query, item.triggerTerms)) {
                item.ruleMatched = true;
                candidateIds.add(item.version.getId());
            }
        addKeywordCandidates(query, available, candidateIds);
        addSemanticCandidates(query, available, candidateIds);
        if (candidateIds.isEmpty()) {
            decision.setReason("NO_CANDIDATE");
            return cache(cacheKey, decision, startedAt);
        }
        List<Candidate> candidates = available.stream().filter(c -> candidateIds.contains(c.version.getId())).sorted(Comparator.comparing((Candidate c) -> !c.ruleMatched).thenComparing(c -> !c.keywordMatched).thenComparing(c -> c.priority == null ? Integer.MAX_VALUE : c.priority).thenComparing(c -> c.vectorScore == null ? Double.NEGATIVE_INFINITY : -c.vectorScore)).limit(MAX_CANDIDATES).collect(Collectors.toList());
        for (Candidate item : candidates) decision.getCandidates().add(item.audit());
        return cache(cacheKey, classify(agent, chatProvider, query, candidates, decision), startedAt);
    }

    /**
     * 缓存当前请求。
     */
    private SkillRouteDecision cache(String cacheKey, SkillRouteDecision decision, long startedAt) {
        if (!"ROUTER_UNAVAILABLE".equals(decision.getReason())) {
            evictRouteCache();
            routeCache.put(cacheKey, new CachedRoute(copy(decision), System.currentTimeMillis() + ROUTE_CACHE_TTL_MS));
        }
        ChatLatencyMetrics.record("chat.skill_route", System.currentTimeMillis() - startedAt);
        return decision;
    }

    /**
     * 处理route缓存Key。
     */
    private String routeCacheKey(AgentDefinition agent, String query, List<AgentDefinitionSkillBinding> bindings) {
        String installedVersions = bindings.stream().map(binding -> StringUtils.defaultString(binding.getSkillVersionId()) + ':'
                        + StringUtils.defaultString(binding.getSkillId()) + ':' + StringUtils.defaultString(binding.getPriority() == null ? null : binding.getPriority().toString())
                        + ':' + StringUtils.defaultString(binding.getStatus() == null ? null : binding.getStatus().toString()))
                .sorted().collect(Collectors.joining("|"));
        return StringUtils.defaultString(agent == null ? null : agent.getId()) + '|' + query.trim().replaceAll("\\s+", " ").toLowerCase() + '|' + installedVersions;
    }

    /**
     * 处理evictRoute缓存。
     */
    private void evictRouteCache() {
        if (routeCache.size() < ROUTE_CACHE_MAX_SIZE) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, CachedRoute> entry : routeCache.entrySet())
            if (entry.getValue().expiresAt <= now) routeCache.remove(entry.getKey(), entry.getValue());
        if (routeCache.size() >= ROUTE_CACHE_MAX_SIZE) routeCache.clear();
    }

    /**
     * 处理copy。
     */
    private SkillRouteDecision copy(SkillRouteDecision source) {
        SkillRouteDecision copy = new SkillRouteDecision();
        copy.setSkillVersionId(source.getSkillVersionId());
        copy.setReason(source.getReason());
        copy.setConfidence(source.getConfidence());
        for (Map<String, Object> candidate : source.getCandidates())
            copy.getCandidates().add(new LinkedHashMap<>(candidate));
        return copy;
    }

    /**
     * 当前RequestId。
     */
    private String currentRequestId() {
        return StringUtils.defaultIfBlank(MDC.get("chatRequestId"), "n/a");
    }

    /**
     * 处理candidate。
     */
    private Candidate candidate(AgentDefinitionSkillBinding binding) {
        AgentSkill skill = skillService.getById(binding.getSkillId());
        AgentSkillVersion version = versionService.getById(binding.getSkillVersionId());
        if (skill == null || version == null || !Integer.valueOf(1).equals(skill.getStatus()) || !Integer.valueOf(1).equals(version.getStatus()) || StringUtils.isBlank(version.getRoutingSummary()))
            return null;
        return new Candidate(skill, version, parse(version.getTriggerTerms()), parse(version.getExcludeTerms()), parse(version.getRoutingKeywords()), parse(version.getRoutingExamples()), binding.getPriority());
    }

    /**
     * 新增KeywordCandidates。
     */
    private void addKeywordCandidates(String query, List<Candidate> available, Set<String> output) {
        for (Candidate item : available)
            if (!matchesAny(query, item.excludeTerms) && matchesAny(query, item.keywords)) {
                item.keywordMatched = true;
                output.add(item.version.getId());
            }
    }

    /**
     * 新增SemanticCandidates。
     */
    private void addSemanticCandidates(String query, List<Candidate> available, Set<String> output) {
        String embeddingModelId = routingConfigService.embeddingModelId();
        if (StringUtils.isBlank(embeddingModelId) || available.isEmpty()) return;
        try {
            ModelProvider provider = modelCatalogService.resolveProvider(embeddingModelId, "EMBEDDING");
            String vector = embeddingService.toVectorLiteral(embeddingService.embed(provider, query));
            List<AgentSkillRoutingIndex> hits = indexMapper.findSimilar(available.stream().map(c -> c.version.getId()).collect(Collectors.toList()), vector, MAX_CANDIDATES);
            Map<String, Candidate> byVersion = available.stream().collect(Collectors.toMap(c -> c.version.getId(), c -> c));
            for (AgentSkillRoutingIndex hit : hits) {
                Candidate candidate = byVersion.get(hit.getSkillVersionId());
                if (candidate != null && !matchesAny(query, candidate.excludeTerms)) {
                    candidate.vectorScore = hit.getVectorScore();
                    output.add(hit.getSkillVersionId());
                }
            }
        } catch (Exception ignored) { /* semantic routing is an optional recall source */ }
    }

    /**
     * 处理classify。
     */
    private SkillRouteDecision classify(AgentDefinition agent, ModelProvider provider, String query, List<Candidate> candidates, SkillRouteDecision decision) {
        try {
            ModelChatRequest request = new ModelChatRequest();
            request.setAgent(agent);
            request.setProvider(provider);
            request.setTemperature(BigDecimal.ZERO);
            request.setMaxCompletionTokens(80);
            request.setResponseFormat(Collections.<String, Object>singletonMap("type", "json_object"));
            request.setMessages(Collections.singletonList(new ModelChatMessage("user", prompt(query, candidates))));
            ModelChatResponse response = modelClientFactory.getClient(provider).chat(request);
            JSONObject json = JSON.parseObject(response == null ? null : response.getContent());
            String id = json == null ? null : json.getString("skillVersionId");
            Double confidence = json == null ? null : json.getDouble("confidence");
            if ("NONE".equalsIgnoreCase(id) || confidence == null || confidence < 0.60 || candidates.stream().noneMatch(c -> c.version.getId().equals(id))) {
                decision.setReason("LOW_CONFIDENCE_OR_NONE");
                decision.setConfidence(confidence);
                return decision;
            }
            decision.setSkillVersionId(id);
            decision.setConfidence(confidence);
            decision.setReason(StringUtils.abbreviate(json.getString("reason"), 200));
            return decision;
        } catch (Exception e) {
            decision.setReason("ROUTER_UNAVAILABLE");
            return decision;
        }
    }

    /**
     * 处理prompt。
     */
    private String prompt(String query, List<Candidate> candidates) {
        StringBuilder out = new StringBuilder("Select exactly one relevant Skill or NONE. Return JSON only: {\"skillVersionId\":\"id|NONE\",\"confidence\":0..1,\"reason\":\"short\"}. Do not answer the user.\nUser task:\n").append(query).append("\nCandidates:\n");
        for (Candidate c : candidates) out.append(JSON.toJSONString(c.metadata())).append('\n');
        return out.toString();
    }

    /**
     * 处理matchesAny。
     */
    private boolean matchesAny(String query, List<String> terms) {
        String source = query.toLowerCase();
        for (String term : terms) if (StringUtils.isNotBlank(term) && source.contains(term.toLowerCase())) return true;
        return false;
    }

    /**
     * 解析当前请求。
     */
    private List<String> parse(String text) {
        try {
            List<String> result = JSON.parseArray(StringUtils.defaultIfBlank(text, "[]"), String.class);
            return result == null ? Collections.<String>emptyList() : result;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 表示CachedRoute。
     */
    private static class CachedRoute {
        final SkillRouteDecision decision;
        final long expiresAt;

        /**
         * 创建 {@code CachedRoute} 实例。
         */
        CachedRoute(SkillRouteDecision decision, long expiresAt) {
            this.decision = decision;
            this.expiresAt = expiresAt;
        }
    }

    /**
     * 表示Candidate。
     */
    private static class Candidate {
        final AgentSkill skill;
        final AgentSkillVersion version;
        final List<String> triggerTerms, excludeTerms, keywords, examples;
        final Integer priority;
        boolean ruleMatched;
        boolean keywordMatched;
        Double vectorScore;

        /**
         * 创建 {@code Candidate} 实例。
         */
        Candidate(AgentSkill s, AgentSkillVersion v, List<String> t, List<String> e, List<String> k, List<String> x, Integer p) {
            skill = s;
            version = v;
            triggerTerms = t;
            excludeTerms = e;
            keywords = k;
            examples = x;
            priority = p;
        }

        /**
         * 处理metadata。
         */
        Map<String, Object> metadata() {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("skillVersionId", version.getId());
            r.put("name", skill.getName());
            r.put("summary", version.getRoutingSummary());
            r.put("category", skill.getCategory());
            r.put("tags", skill.getTags());
            r.put("examples", examples);
            return r;
        }

        /**
         * 处理audit。
         */
        Map<String, Object> audit() {
            Map<String, Object> r = metadata();
            r.put("ruleMatched", ruleMatched);
            r.put("keywordMatched", keywordMatched);
            r.put("vectorScore", vectorScore);
            r.put("priority", priority);
            return r;
        }
    }
}
