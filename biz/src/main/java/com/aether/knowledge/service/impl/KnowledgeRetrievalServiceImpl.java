package com.aether.knowledge.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.agent.entity.AgentKnowledgeBaseBinding;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.observability.ChatLatencyMetrics;
import com.aether.agent.model.ModelChatMessage;
import com.aether.knowledge.service.KnowledgeDocumentChunkService;
import com.aether.knowledge.service.KnowledgeEmbeddingService;
import com.aether.agent.service.AgentKnowledgeBaseBindingService;
import com.aether.knowledge.service.KnowledgeBaseService;
import com.aether.knowledge.service.KnowledgeRetrievalService;
import com.aether.knowledge.service.KnowledgeRerankService;
import com.aether.local.CurrentUser;
import com.aether.knowledge.model.KnowledgeBaseScope;
import com.aether.knowledge.model.KnowledgeRetrievalResult;
import com.aether.agent.service.ModelProviderService;
import com.aether.agent.service.QueryRewriteService;
import com.aether.agent.service.ModelCatalogService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 知识库检索实现：混合召回、重排序、邻块扩展和上下文预算控制。
 */
@Service
public class KnowledgeRetrievalServiceImpl implements KnowledgeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalServiceImpl.class);
    private static final int STATUS_ENABLED = 1;
    private static final int KB_INDEX_STATUS_DONE = 2;
    private static final int DEFAULT_TOP_K = 6;
    private static final int MAX_TOP_K = 20;
    private static final int DEFAULT_MAX_CHUNKS_PER_DOCUMENT = 4;
    private static final int MAX_CHUNKS_PER_DOCUMENT = 10;
    private static final int CANDIDATE_MULTIPLIER = 4;
    /**
     * 每个语义命中点向前后恢复的邻接分块数量。
     */
    private static final int DEFAULT_NEIGHBOR_RADIUS = 1;
    /**
     * 检索上下文的 token 上限，为历史消息和工具输出预留空间。
     */
    private static final int MAX_CONTEXT_TOKENS = 12000;
    private static final long QUERY_EMBEDDING_CACHE_TTL_MS = 5 * 60 * 1000L;
    private static final int QUERY_EMBEDDING_CACHE_MAX_SIZE = 2000;
    private static final long RETRIEVAL_CACHE_TTL_MS = 60 * 1000L;
    private static final int RETRIEVAL_CACHE_MAX_SIZE = 1000;
    @Value("${aether.reliability.circuit-breaker.failure-threshold:3}")
    private int providerFailureThreshold = 3;
    @Value("${aether.reliability.circuit-breaker.cooldown-ms:30000}")
    private long providerCircuitCooldownMs = 30 * 1000L;
    private static final long RERANK_NOT_FOUND_COOLDOWN_MS = 5 * 60 * 1000L;
    private static final long QUERY_REWRITE_CACHE_TTL_MS = 5 * 60 * 1000L;
    private static final int QUERY_REWRITE_CACHE_MAX_SIZE = 1000;
    private static final double DEFAULT_MIN_SIMILARITY = 0.30D;
    private static final double ADAPTIVE_SIMILARITY_RELAXATION = 0.08D;
    private static final double ADAPTIVE_SIMILARITY_FLOOR = 0.18D;
    private static final double DEFAULT_MIN_LEXICAL_SCORE = 0.05D;
    private static final double DEFAULT_VECTOR_WEIGHT = 0.70D;
    private static final Pattern EXACT_TERM = Pattern.compile("(?i)[a-z][a-z0-9._/-]{2,}|\\d{4}[-/]?\\d{1,2}[-/]?\\d{1,2}|\\d{3,}");

    private final KnowledgeBaseService knowledgeBaseService;
    private final AgentKnowledgeBaseBindingService bindingService;
    private final KnowledgeDocumentChunkService knowledgeDocumentChunkService;
    private final ModelProviderService modelProviderService;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;
    private final KnowledgeRerankService knowledgeRerankService;
    private final QueryRewriteService queryRewriteService;
    private final ModelCatalogService modelCatalogService;
    private final ConcurrentHashMap<String, CachedEmbedding> queryEmbeddingCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedRetrieval> retrievalCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ProviderCircuit> providerCircuits = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> rerankNotFoundUntil = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedQueryRewrite> queryRewriteCache = new ConcurrentHashMap<>();

    /**
     * 创建 {@code KnowledgeRetrievalServiceImpl} 实例。
     */
    @Autowired
    public KnowledgeRetrievalServiceImpl(KnowledgeBaseService knowledgeBaseService,
                                         AgentKnowledgeBaseBindingService bindingService,
                                         KnowledgeDocumentChunkService knowledgeDocumentChunkService,
                                         ModelProviderService modelProviderService,
                                         KnowledgeEmbeddingService knowledgeEmbeddingService,
                                         KnowledgeRerankService knowledgeRerankService,
                                         QueryRewriteService queryRewriteService,
                                         ModelCatalogService modelCatalogService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.bindingService = bindingService;
        this.knowledgeDocumentChunkService = knowledgeDocumentChunkService;
        this.modelProviderService = modelProviderService;
        this.knowledgeEmbeddingService = knowledgeEmbeddingService;
        this.knowledgeRerankService = knowledgeRerankService;
        this.queryRewriteService = queryRewriteService;
        this.modelCatalogService = modelCatalogService;
    }

    /**
     * 创建 {@code KnowledgeRetrievalServiceImpl} 实例。
     */
    public KnowledgeRetrievalServiceImpl(KnowledgeBaseService knowledgeBaseService,
                                         AgentKnowledgeBaseBindingService bindingService,
                                         KnowledgeDocumentChunkService knowledgeDocumentChunkService,
                                         ModelProviderService modelProviderService,
                                         KnowledgeEmbeddingService knowledgeEmbeddingService,
                                         KnowledgeRerankService knowledgeRerankService) {
        this(knowledgeBaseService, bindingService, knowledgeDocumentChunkService, modelProviderService,
                knowledgeEmbeddingService, knowledgeRerankService, null, null);
    }

    /**
     * 创建 {@code KnowledgeRetrievalServiceImpl} 实例。
     */
    public KnowledgeRetrievalServiceImpl(KnowledgeBaseService knowledgeBaseService,
                                         AgentKnowledgeBaseBindingService bindingService,
                                         KnowledgeDocumentChunkService knowledgeDocumentChunkService,
                                         ModelProviderService modelProviderService,
                                         KnowledgeEmbeddingService knowledgeEmbeddingService) {
        this(knowledgeBaseService, bindingService, knowledgeDocumentChunkService, modelProviderService,
                knowledgeEmbeddingService, null, null, null);
    }

    /**
     * 根据 Agent 当前绑定的知识库和检索配置执行一次检索。
     * 流程依次为：解析知识库、查询改写、向量/词法混合召回、重排序、邻块合并和 token 截断。
     */
    @Override
    public KnowledgeRetrievalResult retrieve(String agentDefinitionId, String query) {
        return retrieveInternal(agentDefinitionId, query, null, Collections.<ModelChatMessage>emptyList());
    }

    /**
     * 处理retrieveWith历史记录。
     */
    @Override
    public KnowledgeRetrievalResult retrieveWithHistory(String agentDefinitionId, String query,
                                                        List<ModelChatMessage> history) {
        return retrieveInternal(agentDefinitionId, query, null,
                history == null ? Collections.<ModelChatMessage>emptyList() : history);
    }

    /**
     * 根据 Agent 当前绑定的知识库和检索配置执行一次检索。
     * 流程依次为：解析知识库、查询改写、向量/词法混合召回、重排序、邻块合并和 token 截断。
     */
    @Override
    public KnowledgeRetrievalResult retrieve(String agentDefinitionId, String query, Set<String> scopedKnowledgeBaseIds) {
        return retrieveInternal(agentDefinitionId, query, scopedKnowledgeBaseIds,
                Collections.<ModelChatMessage>emptyList());
    }

    /**
     * 处理retrieveInternal。
     */
    private KnowledgeRetrievalResult retrieveInternal(String agentDefinitionId, String query,
                                                      Set<String> scopedKnowledgeBaseIds,
                                                      List<ModelChatMessage> rewriteHistory) {
        long retrievalStartedAt = System.currentTimeMillis();
        KnowledgeRetrievalResult result = new KnowledgeRetrievalResult();
        if (StringUtils.isBlank(agentDefinitionId) || StringUtils.isBlank(query)) {
            return result;
        }
        String retrievalCacheKey = retrievalCacheKey(agentDefinitionId + ":" + (scopedKnowledgeBaseIds == null ? "default" : scopedKnowledgeBaseIds.toString()), query);
        CachedRetrieval cachedRetrieval = retrievalCache.get(retrievalCacheKey);
        if (cachedRetrieval != null && cachedRetrieval.expiresAt > System.currentTimeMillis()) {
            log.info("知识检索完成: requestId={}, agentId={}, cacheHit=true, total={}ms, chunks={}",
                    currentRequestId(), agentDefinitionId, System.currentTimeMillis() - retrievalStartedAt,
                    cachedRetrieval.result.getChunks() == null ? 0 : cachedRetrieval.result.getChunks().size());
            return copyResult(cachedRetrieval.result);
        }
        try {
            List<AgentKnowledgeBaseBinding> bindings = bindingService.list(Wrappers.lambdaQuery(AgentKnowledgeBaseBinding.class)
                    .eq(AgentKnowledgeBaseBinding::getAgentDefinitionId, agentDefinitionId)
                    .eq(AgentKnowledgeBaseBinding::getStatus, STATUS_ENABLED)
                    .eq(AgentKnowledgeBaseBinding::getDeleted, false));
            List<String> boundKbIds = bindings == null ? new ArrayList<>() : bindings.stream()
                    .map(AgentKnowledgeBaseBinding::getKnowledgeBaseId)
                    .filter(StringUtils::isNotBlank)
                    .distinct()
                    .collect(Collectors.toList());
            if (scopedKnowledgeBaseIds != null) {
                boundKbIds.retainAll(scopedKnowledgeBaseIds);
            }
            if (boundKbIds.isEmpty()) return result;
            List<KnowledgeBase> knowledgeBases = knowledgeBaseService.list(Wrappers.lambdaQuery(KnowledgeBase.class)
                    .in(KnowledgeBase::getId, boundKbIds)
                    .eq(KnowledgeBase::getStatus, STATUS_ENABLED)
                    .eq(KnowledgeBase::getIndexStatus, KB_INDEX_STATUS_DONE)
                    .eq(KnowledgeBase::getDeleted, false));
            if (knowledgeBases == null || knowledgeBases.isEmpty()) {
                return result;
            }
            Map<String, ModelProvider> providers = new LinkedHashMap<>();
            Map<String, List<String>> knowledgeBaseIdsByProvider = new LinkedHashMap<>();
            Map<String, RetrievalConfig> retrievalConfigs = new HashMap<>();
            for (KnowledgeBase knowledgeBase : knowledgeBases) {
                RetrievalConfig retrievalConfig = parseRetrievalConfig(knowledgeBase.getRetrievalConfig());
                retrievalConfigs.put(knowledgeBase.getId(), retrievalConfig);
                result.setStrictGrounding(result.isStrictGrounding() || retrievalConfig.strictGrounding);
                ModelProvider provider = getEmbeddingProvider(knowledgeBase);
                if (provider == null || Boolean.TRUE.equals(provider.getDeleted())
                        || !Integer.valueOf(STATUS_ENABLED).equals(provider.getStatus())) {
                    log.warn("知识库未配置可用的向量模型供应商: knowledgeBaseId={}", knowledgeBase.getId());
                    continue;
                }
                providers.put(provider.getId(), provider);
                knowledgeBaseIdsByProvider.computeIfAbsent(provider.getId(), key -> new ArrayList<>())
                        .add(knowledgeBase.getId());
            }

            List<KnowledgeDocumentChunk> chunks = new ArrayList<>();
            result.setRetrievalAttempted(!knowledgeBaseIdsByProvider.isEmpty());
            boolean providerSucceeded = false;
            for (Map.Entry<String, List<String>> entry : knowledgeBaseIdsByProvider.entrySet()) {
                ModelProvider provider = providers.get(entry.getKey());
                if (isProviderCircuitOpen(provider.getId())) {
                    log.warn("知识库向量供应商处于熔断冷却期，已跳过: providerId={}", provider.getId());
                    continue;
                }
                try {
                    // Query each base separately. A highly similar large base must not consume
                    // another base's configured recall quota before thresholding takes place.
                    for (String knowledgeBaseId : entry.getValue()) {
                        RetrievalConfig config = retrievalConfigs.get(knowledgeBaseId);
                        if (config == null) {
                            continue;
                        }
                        long rewriteStartedAt = System.currentTimeMillis();
                        String effectiveQuery = rewriteQuery(query, config, rewriteHistory);
                        long vectorStartedAt = System.currentTimeMillis();
                        int retrievalLimit = Math.min(MAX_TOP_K * CANDIDATE_MULTIPLIER,
                                config.topK * CANDIDATE_MULTIPLIER);
                        List<KnowledgeDocumentChunk> vectorCandidates = searchVectorCandidates(
                                provider, knowledgeBaseId, effectiveQuery, retrievalLimit);
                        long vectorCompletedAt = System.currentTimeMillis();
                        List<KnowledgeDocumentChunk> lexicalCandidates = Collections.emptyList();
                        if (config.hybridEnabled) {
                            try {
                                lexicalCandidates = searchLexicalCandidates(knowledgeBaseId, effectiveQuery, retrievalLimit);
                            } catch (Exception e) {
                                log.warn("知识库全文检索失败，已降级为向量检索: knowledgeBaseId={}", knowledgeBaseId, e);
                            }
                        }
                        long lexicalCompletedAt = System.currentTimeMillis();
                        List<KnowledgeDocumentChunk> fusedCandidates = fuseCandidates(vectorCandidates, lexicalCandidates, config);
                        List<KnowledgeDocumentChunk> rankedCandidates = rerankCandidates(fusedCandidates, config, effectiveQuery);
                        long rerankCompletedAt = System.currentTimeMillis();
                        chunks.addAll(selectKnowledgeBaseChunks(rankedCandidates, config));
                        log.info("知识检索分段: requestId={}, knowledgeBaseId={}, rewrite={}ms, vector={}ms, lexical={}ms, rerank={}ms, vectorCandidates={}, lexicalCandidates={}, rankedCandidates={}",
                                currentRequestId(), knowledgeBaseId, vectorStartedAt - rewriteStartedAt,
                                vectorCompletedAt - vectorStartedAt, lexicalCompletedAt - vectorCompletedAt,
                                rerankCompletedAt - lexicalCompletedAt, vectorCandidates.size(), lexicalCandidates.size(),
                                rankedCandidates == null ? 0 : rankedCandidates.size());
                        ChatLatencyMetrics.record("chat.rag.vector", vectorCompletedAt - vectorStartedAt);
                        ChatLatencyMetrics.record("chat.rag.lexical", lexicalCompletedAt - vectorCompletedAt);
                        ChatLatencyMetrics.record("chat.rag.rerank", rerankCompletedAt - lexicalCompletedAt);
                    }
                    providerCircuits.remove(circuitKey(provider.getId()));
                    providerSucceeded = true;
                } catch (Exception e) {
                    // One unavailable embedding provider must not prevent other
                    // knowledge bases from participating in retrieval.
                    log.warn("知识库向量检索失败，已跳过该供应商: providerId={}", provider.getId(), e);
                    recordProviderFailure(provider.getId());
                }
            }
            result.setRetrievalFailed(result.isRetrievalAttempted() && !providerSucceeded);
            if (chunks.isEmpty()) {
                return cacheResult(retrievalCacheKey, result);
            }
            chunks = selectDiverseChunks(chunks);
            if (chunks.size() > MAX_TOP_K) {
                chunks = new ArrayList<>(chunks.subList(0, MAX_TOP_K));
            }
            chunks = buildContextGroups(chunks);
            chunks = applyContextTokenBudget(chunks);
            if (chunks.isEmpty()) {
                return cacheResult(retrievalCacheKey, result);
            }
            StringBuilder builder = new StringBuilder("【知识库检索结果】\n");
            int i = 1;
            for (KnowledgeDocumentChunk chunk : chunks) {
                builder.append("片段 ").append(i++).append("：\n")
                        .append(chunk.getContent()).append("\n\n");
            }
            builder.append("请优先依据以上知识片段回答；若片段不足以支持结论，请明确说明。");
            result.setContext(builder.toString());
            result.setChunks(chunks);
            log.info("知识检索完成: requestId={}, agentId={}, cacheHit=false, total={}ms, chunks={}",
                    currentRequestId(), agentDefinitionId, System.currentTimeMillis() - retrievalStartedAt, chunks.size());
            ChatLatencyMetrics.record("chat.rag.total", System.currentTimeMillis() - retrievalStartedAt);
            return cacheResult(retrievalCacheKey, result);
        } catch (Exception e) {
            log.warn("知识库检索失败，已降级为无知识上下文: agentId={}", agentDefinitionId, e);
            return result;
        }
    }

    /**
     * 获取EmbeddingProvider。
     */
    private ModelProvider getEmbeddingProvider(KnowledgeBase knowledgeBase) {
        if (modelCatalogService == null || StringUtils.isBlank(knowledgeBase.getEmbeddingModelId())) return null;
        try {
            return modelCatalogService.resolveProvider(knowledgeBase.getEmbeddingModelId(), "EMBEDDING");
        } catch (Exception e) {
            log.warn("知识库向量模型目录项不可用: knowledgeBaseId={}", knowledgeBase.getId());
            return null;
        }
    }

    /**
     * 获取查询Embedding。
     */
    private List<Double> getQueryEmbedding(ModelProvider provider, String query) {
        // 缓存键与向量模型必须使用同一份规范化查询，避免空白差异产生重复向量和缓存穿透。
        String normalizedQuery = normalizeQuery(query);
        String cacheKey = queryEmbeddingCacheKey(provider, normalizedQuery);
        long now = System.currentTimeMillis();
        CachedEmbedding cached = queryEmbeddingCache.get(cacheKey);
        if (cached != null && cached.expiresAt > now) {
            log.info("查询向量缓存命中: requestId={}, providerId={}", currentRequestId(), provider.getId());
            return new ArrayList<>(cached.embedding);
        }
        synchronized (queryEmbeddingCache) {
            now = System.currentTimeMillis();
            cached = queryEmbeddingCache.get(cacheKey);
            if (cached != null && cached.expiresAt > now) {
                log.info("查询向量缓存命中: requestId={}, providerId={}", currentRequestId(), provider.getId());
                return new ArrayList<>(cached.embedding);
            }
            if (cached != null) {
                queryEmbeddingCache.remove(cacheKey, cached);
            }
            evictQueryEmbeddingCache(now);
            long embeddingStartedAt = System.currentTimeMillis();
            List<Double> embedding = knowledgeEmbeddingService.embed(provider, normalizedQuery);
            log.info("查询向量生成: requestId={}, providerId={}, duration={}ms", currentRequestId(),
                    provider.getId(), System.currentTimeMillis() - embeddingStartedAt);
            ChatLatencyMetrics.record("chat.rag.embedding", System.currentTimeMillis() - embeddingStartedAt);
            if (embedding == null || embedding.isEmpty()) {
                return embedding;
            }
            CachedEmbedding fresh = new CachedEmbedding(Collections.unmodifiableList(new ArrayList<>(embedding)),
                    now + QUERY_EMBEDDING_CACHE_TTL_MS);
            queryEmbeddingCache.put(cacheKey, fresh);
            return new ArrayList<>(fresh.embedding);
        }
    }

    /**
     * 处理evict查询Embedding缓存。
     */
    private void evictQueryEmbeddingCache(long now) {
        if (queryEmbeddingCache.size() < QUERY_EMBEDDING_CACHE_MAX_SIZE) {
            return;
        }
        for (Map.Entry<String, CachedEmbedding> entry : queryEmbeddingCache.entrySet()) {
            if (entry.getValue().expiresAt <= now) {
                queryEmbeddingCache.remove(entry.getKey(), entry.getValue());
            }
        }
        // A bounded local cache is a performance optimization only. When all entries are hot,
        // clear it instead of retaining unbounded query-derived data in the application process.
        if (queryEmbeddingCache.size() >= QUERY_EMBEDDING_CACHE_MAX_SIZE) {
            queryEmbeddingCache.clear();
        }
    }

    /**
     * 查询Embedding缓存Key。
     */
    private String queryEmbeddingCacheKey(ModelProvider provider, String query) {
        String normalizedQuery = normalizeQuery(query);
        String value = StringUtils.defaultString(provider.getId()) + '\n'
                + StringUtils.defaultString(provider.getApiBaseUrl()) + '\n'
                + StringUtils.defaultString(provider.getDefaultModel()) + '\n' + normalizedQuery;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception e) {
            // SHA-256 is mandatory in a Java runtime; retain a safe fallback for custom runtimes.
            return Integer.toHexString(value.hashCode());
        }
    }

    /**
     * 处理retrieval缓存Key。
     */
    private String retrievalCacheKey(String agentDefinitionId, String query) {
        String tenantId = CurrentUser.getUser() == null ? "" : CurrentUser.getUser().get("tenantId");
        return hashValue(StringUtils.defaultString(tenantId) + '\n' + agentDefinitionId + '\n' + normalizeQuery(query));
    }

    /**
     * 统一查询规范化规则，确保检索缓存与向量调用语义一致。
     */
    private String normalizeQuery(String query) {
        return StringUtils.defaultString(query).trim().replaceAll("\\s+", " ");
    }

    /**
     * 处理hashValue。
     */
    private String hashValue(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) builder.append(String.format("%02x", item));
            return builder.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    /**
     * 缓存结果。
     */
    private KnowledgeRetrievalResult cacheResult(String cacheKey, KnowledgeRetrievalResult result) {
        evictRetrievalCache();
        retrievalCache.put(cacheKey, new CachedRetrieval(copyResult(result), System.currentTimeMillis() + RETRIEVAL_CACHE_TTL_MS));
        return result;
    }

    /**
     * 处理evictRetrieval缓存。
     */
    private void evictRetrievalCache() {
        if (retrievalCache.size() < RETRIEVAL_CACHE_MAX_SIZE) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, CachedRetrieval> entry : retrievalCache.entrySet()) {
            if (entry.getValue().expiresAt <= now) retrievalCache.remove(entry.getKey(), entry.getValue());
        }
        if (retrievalCache.size() >= RETRIEVAL_CACHE_MAX_SIZE) retrievalCache.clear();
    }

    /**
     * 处理copy结果。
     */
    private KnowledgeRetrievalResult copyResult(KnowledgeRetrievalResult source) {
        KnowledgeRetrievalResult copy = new KnowledgeRetrievalResult();
        copy.setRetrievalAttempted(source.isRetrievalAttempted());
        copy.setStrictGrounding(source.isStrictGrounding());
        copy.setContext(source.getContext());
        copy.setChunks(source.getChunks() == null ? Collections.<KnowledgeDocumentChunk>emptyList()
                : new ArrayList<>(source.getChunks()));
        return copy;
    }

    /**
     * 判断是否为ProviderCircuitOpen。
     */
    private boolean isProviderCircuitOpen(String providerId) {
        ProviderCircuit circuit = providerCircuits.get(circuitKey(providerId));
        if (circuit == null) return false;
        synchronized (circuit) {
            if (circuit.openUntil > System.currentTimeMillis()) return true;
            if (circuit.openUntil > 0) {
                circuit.openUntil = 0;
                circuit.failures = 0;
            }
            return false;
        }
    }

    /**
     * 处理recordProviderFailure。
     */
    private void recordProviderFailure(String providerId) {
        ProviderCircuit circuit = providerCircuits.computeIfAbsent(circuitKey(providerId), key -> new ProviderCircuit());
        synchronized (circuit) {
            circuit.failures++;
            if (circuit.failures >= providerFailureThreshold) {
                circuit.openUntil = System.currentTimeMillis() + providerCircuitCooldownMs;
            }
        }
    }

    private String circuitKey(String providerId) {
        String tenantId = CurrentUser.getUser() == null ? "public" : CurrentUser.getUser().get("tenantId");
        return StringUtils.defaultIfBlank(tenantId, "public") + ":" + providerId;
    }

    /**
     * 解析Retrieval配置。
     */
    private RetrievalConfig parseRetrievalConfig(String value) {
        int topK = DEFAULT_TOP_K;
        double minSimilarity = DEFAULT_MIN_SIMILARITY;
        int maxChunksPerDocument = DEFAULT_MAX_CHUNKS_PER_DOCUMENT;
        boolean hybridEnabled = true;
        double vectorWeight = DEFAULT_VECTOR_WEIGHT;
        double minLexicalScore = DEFAULT_MIN_LEXICAL_SCORE;
        double authorityScore = 0D;
        double authorityWeight = 0D;
        double freshnessWeight = 0D;
        boolean rerankEnabled = false;
        String rerankModelId = null;
        int rerankTopN = DEFAULT_TOP_K;
        boolean queryRewriteEnabled = false;
        String queryRewriteModelId = null;
        boolean strictGrounding = false;
        if (StringUtils.isNotBlank(value)) {
            try {
                JSONObject json = JSONObject.parseObject(value);
                Integer configuredTopK = json.getInteger("topK");
                Integer configuredDocumentLimit = json.getInteger("maxChunksPerDocument");
                Boolean configuredHybridEnabled = json.getBoolean("hybridEnabled");
                Double configuredVectorWeight = json.getDouble("vectorWeight");
                Double configuredMinLexicalScore = json.getDouble("minLexicalScore");
                Boolean configuredRerankEnabled = json.getBoolean("rerankEnabled");
                Boolean configuredQueryRewriteEnabled = json.getBoolean("queryRewriteEnabled");
                String configuredQueryRewriteModelId = json.getString("queryRewriteModelId");
                String configuredRerankModelId = json.getString("rerankModelId");
                Integer configuredRerankTopN = json.getInteger("rerankTopN");
                Boolean configuredStrictGrounding = json.getBoolean("strictGrounding");
                Double configuredAuthorityScore = json.getDouble("authorityScore");
                Double configuredAuthorityWeight = json.getDouble("authorityWeight");
                Double configuredFreshnessWeight = json.getDouble("freshnessWeight");
                Double configuredThreshold = json.getDouble("minSimilarity");
                if (configuredThreshold == null) {
                    configuredThreshold = json.getDouble("scoreThreshold");
                }
                if (configuredTopK != null) {
                    topK = Math.max(1, Math.min(MAX_TOP_K, configuredTopK));
                }
                if (configuredDocumentLimit != null) {
                    maxChunksPerDocument = Math.max(1, Math.min(MAX_CHUNKS_PER_DOCUMENT, configuredDocumentLimit));
                }
                if (configuredHybridEnabled != null) {
                    hybridEnabled = configuredHybridEnabled;
                }
                if (configuredVectorWeight != null) {
                    vectorWeight = Math.max(0D, Math.min(1D, configuredVectorWeight));
                }
                if (configuredMinLexicalScore != null) {
                    minLexicalScore = Math.max(0D, Math.min(1D, configuredMinLexicalScore));
                }
                if (configuredRerankEnabled != null) {
                    rerankEnabled = configuredRerankEnabled;
                }
                if (configuredQueryRewriteEnabled != null) queryRewriteEnabled = configuredQueryRewriteEnabled;
                queryRewriteModelId = configuredQueryRewriteModelId;
                rerankModelId = configuredRerankModelId;
                if (configuredRerankTopN != null) {
                    rerankTopN = Math.max(1, Math.min(MAX_TOP_K, configuredRerankTopN));
                }
                if (configuredStrictGrounding != null) {
                    strictGrounding = configuredStrictGrounding;
                }
                if (configuredAuthorityScore != null) authorityScore = clampUnit(configuredAuthorityScore);
                if (configuredAuthorityWeight != null) authorityWeight = clampUnit(configuredAuthorityWeight);
                if (configuredFreshnessWeight != null) freshnessWeight = clampUnit(configuredFreshnessWeight);
                if (configuredThreshold != null) {
                    minSimilarity = Math.max(-1D, Math.min(1D, configuredThreshold));
                }
            } catch (Exception e) {
                log.warn("Invalid knowledge retrieval config, using defaults", e);
            }
        }
        return new RetrievalConfig(topK, minSimilarity, maxChunksPerDocument,
                hybridEnabled, vectorWeight, minLexicalScore, rerankEnabled,
                rerankModelId, rerankTopN, strictGrounding,
                authorityScore, authorityWeight, freshnessWeight, queryRewriteEnabled,
                queryRewriteModelId);
    }

    /**
     * 对宽召回候选集调用 reranker，并限制最终上下文数量。
     */
    private List<KnowledgeDocumentChunk> rerankCandidates(List<KnowledgeDocumentChunk> candidates,
                                                          RetrievalConfig config, String query) {
        if (!config.rerankEnabled || knowledgeRerankService == null || candidates == null || candidates.isEmpty()
                || StringUtils.isBlank(config.rerankModelId)) {
            return candidates;
        }
        ModelProvider provider = resolveProvider(config.rerankModelId, "RERANK");
        if (provider == null || Boolean.TRUE.equals(provider.getDeleted())
                || !Integer.valueOf(STATUS_ENABLED).equals(provider.getStatus())) {
            log.warn("知识库重排序模型不可用，使用融合排序: modelId={}", config.rerankModelId);
            return candidates;
        }
        Long unavailableUntil = rerankNotFoundUntil.get(provider.getId());
        if (unavailableUntil != null && unavailableUntil > System.currentTimeMillis()) {
            return candidates;
        }
        try {
            List<KnowledgeDocumentChunk> ranked = knowledgeRerankService.rerank(provider, provider.getDefaultModel(), query, candidates, config.rerankTopN);
            rerankNotFoundUntil.remove(provider.getId());
            return applyRankingPolicy(ranked, config);
        } catch (Exception e) {
            if (e instanceof HttpClientErrorException.NotFound) {
                rerankNotFoundUntil.put(provider.getId(), System.currentTimeMillis() + RERANK_NOT_FOUND_COOLDOWN_MS);
                log.warn("知识库重排序接口不存在，5 分钟内跳过该供应商: providerId={}", provider.getId());
            } else {
                log.warn("知识库重排序失败，使用融合排序: providerId={}, reason={}", provider.getId(), e.getMessage());
            }
            return candidates;
        }
    }

    /**
     * 当前RequestId。
     */
    private String currentRequestId() {
        return StringUtils.defaultIfBlank(MDC.get("chatRequestId"), "n/a");
    }

    /**
     * 解析Provider。
     */
    private ModelProvider resolveProvider(String modelId, String capability) {
        if (modelCatalogService == null || StringUtils.isBlank(modelId)) return null;
        try {
            return modelCatalogService.resolveProvider(modelId, capability);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 处理rewrite查询。
     */
    private String rewriteQuery(String query, RetrievalConfig config, List<ModelChatMessage> history) {
        if (!config.queryRewriteEnabled || queryRewriteService == null
                || StringUtils.isBlank(config.queryRewriteModelId)) return query;
        if (!requiresConversationContext(query, history)) {
            log.debug("跳过知识库查询重写（独立问句）: requestId={}", currentRequestId());
            return query;
        }
        ModelProvider provider = resolveProvider(config.queryRewriteModelId, "CHAT,MULTIMODAL");
        if (provider == null || Boolean.TRUE.equals(provider.getDeleted())
                || !Integer.valueOf(STATUS_ENABLED).equals(provider.getStatus())) {
            log.warn("知识库查询重写模型不可用，使用原查询: modelId={}", config.queryRewriteModelId);
            return query;
        }
        String cacheKey = retrievalCacheKey("rewrite:" + provider.getId() + ":"
                + StringUtils.defaultString(provider.getDefaultModel()), query);
        CachedQueryRewrite cached = queryRewriteCache.get(cacheKey);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) return cached.query;
        String rewritten = queryRewriteService.rewrite(history, query, null, provider,
                provider.getDefaultModel()).getRewrittenContent();
        String effectiveQuery = StringUtils.defaultIfBlank(rewritten, query);
        evictQueryRewriteCache();
        queryRewriteCache.put(cacheKey, new CachedQueryRewrite(effectiveQuery,
                System.currentTimeMillis() + QUERY_REWRITE_CACHE_TTL_MS));
        return effectiveQuery;
    }

    /**
     * Query rewriting only helps when the new turn refers to earlier context.
     */
    private boolean requiresConversationContext(String query, List<ModelChatMessage> history) {
        if (history == null || history.isEmpty()) return false;
        String normalized = StringUtils.defaultString(query).toLowerCase();
        return normalized.contains("这") || normalized.contains("那") || normalized.contains("它")
                || normalized.contains("上述") || normalized.contains("前述") || normalized.contains("之前")
                || normalized.contains("刚才") || normalized.contains("继续") || normalized.contains("同样")
                || normalized.contains("其中") || normalized.contains("上面") || normalized.contains("这个")
                || normalized.contains("那个") || normalized.contains("第二个") || normalized.contains("第一个");
    }

    /**
     * 处理evict查询Rewrite缓存。
     */
    private void evictQueryRewriteCache() {
        if (queryRewriteCache.size() < QUERY_REWRITE_CACHE_MAX_SIZE) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, CachedQueryRewrite> entry : queryRewriteCache.entrySet()) {
            if (entry.getValue().expiresAt <= now) queryRewriteCache.remove(entry.getKey(), entry.getValue());
        }
        if (queryRewriteCache.size() >= QUERY_REWRITE_CACHE_MAX_SIZE) queryRewriteCache.clear();
    }

    /**
     * 处理applyRankingPolicy。
     */
    private List<KnowledgeDocumentChunk> applyRankingPolicy(List<KnowledgeDocumentChunk> candidates, RetrievalConfig config) {
        if (candidates == null || candidates.isEmpty() || (config.authorityWeight == 0D && config.freshnessWeight == 0D)) {
            return candidates;
        }
        long now = System.currentTimeMillis();
        for (KnowledgeDocumentChunk candidate : candidates) {
            double base = candidate.getRetrievalScore() == null ? 0D : candidate.getRetrievalScore();
            double freshness = freshnessScore(candidate.getUpdatedAt(), now);
            candidate.setRetrievalScore(base + config.authorityWeight * config.authorityScore
                    + config.freshnessWeight * freshness);
        }
        candidates.sort(Comparator.comparing(KnowledgeDocumentChunk::getRetrievalScore,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return candidates;
    }

    /**
     * 处理freshnessScore。
     */
    private double freshnessScore(Long updatedAt, long now) {
        if (updatedAt == null || updatedAt <= 0) return 0D;
        long age = Math.max(0L, now - updatedAt);
        long horizon = 90L * 24 * 60 * 60 * 1000;
        return Math.max(0D, 1D - (double) age / horizon);
    }

    /**
     * 处理clampUnit。
     */
    private double clampUnit(double value) {
        return Math.max(0D, Math.min(1D, value));
    }

    /**
     * 使用向量分数和词法分数融合去重，生成排序候选集。
     */
    private List<KnowledgeDocumentChunk> fuseCandidates(List<KnowledgeDocumentChunk> vectorCandidates,
                                                        List<KnowledgeDocumentChunk> lexicalCandidates,
                                                        RetrievalConfig config) {
        Map<String, KnowledgeDocumentChunk> fused = new LinkedHashMap<>();
        if (vectorCandidates != null) {
            for (KnowledgeDocumentChunk candidate : vectorCandidates) {
                candidate.setRetrievalScore(config.vectorWeight * normalizeVectorScore(candidate.getSimilarity()));
                fused.put(candidateKey(candidate), candidate);
            }
        }
        double maxLexicalScore = 0D;
        if (lexicalCandidates != null) {
            for (KnowledgeDocumentChunk candidate : lexicalCandidates) {
                if (candidate.getLexicalScore() != null) {
                    maxLexicalScore = Math.max(maxLexicalScore, candidate.getLexicalScore());
                }
            }
            for (KnowledgeDocumentChunk lexical : lexicalCandidates) {
                KnowledgeDocumentChunk candidate = fused.get(candidateKey(lexical));
                double normalized = normalizeLexicalScore(lexical.getLexicalScore(), maxLexicalScore);
                if (candidate == null) {
                    lexical.setRetrievalScore((1D - config.vectorWeight) * normalized);
                    fused.put(candidateKey(lexical), lexical);
                } else {
                    candidate.setLexicalScore(lexical.getLexicalScore());
                    candidate.setRetrievalScore(candidate.getRetrievalScore() + (1D - config.vectorWeight) * normalized);
                }
            }
        }
        return applyRankingPolicy(new ArrayList<>(fused.values()), config);
    }

    /**
     * 处理searchLexicalCandidates。
     */
    private List<KnowledgeDocumentChunk> searchLexicalCandidates(String knowledgeBaseId, String query, int limit) {
        Map<String, KnowledgeDocumentChunk> merged = new LinkedHashMap<>();
        for (String expandedQuery : buildLexicalQueries(query)) {
            List<KnowledgeDocumentChunk> candidates = knowledgeDocumentChunkService.searchLexicalChunks(
                    Collections.singletonList(knowledgeBaseId), expandedQuery, limit);
            if (candidates != null) {
                for (KnowledgeDocumentChunk candidate : candidates) {
                    String key = candidateKey(candidate);
                    KnowledgeDocumentChunk current = merged.get(key);
                    if (current == null || (candidate.getLexicalScore() != null
                            && (current.getLexicalScore() == null || candidate.getLexicalScore() > current.getLexicalScore()))) {
                        merged.put(key, candidate);
                    }
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * A user question often contains polite wording and interrogatives that
     * dilute its embedding. Search the original question and one compact
     * intent form, then retain the best score for each chunk. The embedding
     * cache keeps repeated wording inexpensive.
     */
    private List<KnowledgeDocumentChunk> searchVectorCandidates(ModelProvider provider, String knowledgeBaseId,
                                                                String query, int limit) {
        Map<String, KnowledgeDocumentChunk> merged = new LinkedHashMap<>();
        for (String vectorQuery : buildVectorQueries(query)) {
            String vector = knowledgeEmbeddingService.toVectorLiteral(getQueryEmbedding(provider, vectorQuery));
            List<KnowledgeDocumentChunk> candidates = knowledgeDocumentChunkService.searchSimilarChunks(
                    Collections.singletonList(knowledgeBaseId), vector, limit);
            if (candidates == null) continue;
            for (KnowledgeDocumentChunk candidate : candidates) {
                String key = candidateKey(candidate);
                KnowledgeDocumentChunk current = merged.get(key);
                if (current == null || (candidate.getSimilarity() != null
                        && (current.getSimilarity() == null || candidate.getSimilarity() > current.getSimilarity()))) {
                    merged.put(key, candidate);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 生成原问题和简化问题，用于扩大向量召回覆盖面。
     */
    private List<String> buildVectorQueries(String query) {
        List<String> queries = new ArrayList<>();
        queries.add(query);
        String intent = query.replaceAll("[，,。！？?；;：:]", " ")
                .replaceAll("请问|麻烦|帮我|能否|可以|如何|怎么|怎样|为什么|是什么|哪些|多少|是否|吗|呢|呀", " ")
                .replaceAll("[的了和与及]", " ").trim().replaceAll("\\s+", " ");
        if (StringUtils.length(intent) >= 2 && !StringUtils.equals(intent, query)) {
            queries.add(intent);
        }
        return queries;
    }

    /**
     * 提取关键词和业务术语，用于词法检索查询改写。
     */
    private List<String> buildLexicalQueries(String query) {
        List<String> queries = new ArrayList<>();
        queries.add(query);
        Matcher matcher = EXACT_TERM.matcher(query);
        while (matcher.find() && queries.size() < 5) {
            String term = matcher.group();
            if (!queries.contains(term)) {
                queries.add(term);
            }
        }
        return queries;
    }

    /**
     * 处理candidateKey。
     */
    private String candidateKey(KnowledgeDocumentChunk candidate) {
        return StringUtils.defaultIfBlank(candidate.getId(),
                StringUtils.defaultIfBlank(candidate.getContentHash(), candidate.getDocumentId() + ":" + candidate.getChunkIndex()));
    }

    /**
     * 规范化VectorScore。
     */
    private double normalizeVectorScore(Double similarity) {
        return similarity == null ? 0D : Math.max(0D, Math.min(1D, (similarity + 1D) / 2D));
    }

    /**
     * 规范化LexicalScore。
     */
    private double normalizeLexicalScore(Double lexicalScore, double maxLexicalScore) {
        return lexicalScore == null || maxLexicalScore <= 0D ? 0D : lexicalScore / maxLexicalScore;
    }

    /**
     * 处理select知识库BaseChunks。
     */
    private List<KnowledgeDocumentChunk> selectKnowledgeBaseChunks(List<KnowledgeDocumentChunk> candidates,
                                                                   RetrievalConfig config) {
        if (candidates == null || candidates.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<KnowledgeDocumentChunk> selected = new ArrayList<>();
        Map<String, Integer> documentCounts = new HashMap<>();
        java.util.Set<String> contentHashes = new java.util.HashSet<>();
        double effectiveMinSimilarity = effectiveMinSimilarity(candidates, config);
        candidates.sort(Comparator.comparing(KnowledgeDocumentChunk::getRetrievalScore,
                Comparator.nullsLast(Comparator.reverseOrder())));
        for (KnowledgeDocumentChunk candidate : candidates) {
            boolean vectorMatch = candidate.getSimilarity() != null && candidate.getSimilarity() >= effectiveMinSimilarity;
            boolean lexicalMatch = config.hybridEnabled && candidate.getLexicalScore() != null
                    && candidate.getLexicalScore() >= config.minLexicalScore;
            if (!vectorMatch && !lexicalMatch) {
                continue;
            }
            String documentId = StringUtils.defaultIfBlank(candidate.getDocumentId(), candidate.getId());
            if (documentCounts.getOrDefault(documentId, 0) >= config.maxChunksPerDocument) {
                continue;
            }
            String contentKey = StringUtils.defaultIfBlank(candidate.getContentHash(), candidate.getContent());
            if (StringUtils.isNotBlank(contentKey) && !contentHashes.add(contentKey)) {
                continue;
            }
            selected.add(candidate);
            documentCounts.put(documentId, documentCounts.getOrDefault(documentId, 0) + 1);
            if (selected.size() == config.topK) {
                break;
            }
        }
        return selected;
    }

    /**
     * 处理effectiveMinSimilarity。
     */
    private double effectiveMinSimilarity(List<KnowledgeDocumentChunk> candidates, RetrievalConfig config) {
        if (config.strictGrounding) return config.minSimilarity;
        boolean hasNormalVectorMatch = false;
        for (KnowledgeDocumentChunk candidate : candidates) {
            if (candidate.getSimilarity() != null && candidate.getSimilarity() >= config.minSimilarity) {
                hasNormalVectorMatch = true;
                break;
            }
        }
        // Only relax when the normal threshold would return nothing from this
        // knowledge base. This improves recall for terse/paraphrased queries
        // without displacing a normally strong match.
        return hasNormalVectorMatch ? config.minSimilarity
                : Math.max(ADAPTIVE_SIMILARITY_FLOOR, config.minSimilarity - ADAPTIVE_SIMILARITY_RELAXATION);
    }

    /**
     * 处理selectDiverseChunks。
     */
    private List<KnowledgeDocumentChunk> selectDiverseChunks(List<KnowledgeDocumentChunk> candidates) {
        candidates.sort(Comparator.comparing(KnowledgeDocumentChunk::getRetrievalScore,
                Comparator.nullsLast(Comparator.reverseOrder())));
        List<KnowledgeDocumentChunk> selected = new ArrayList<>();
        java.util.Set<String> chunkIds = new java.util.HashSet<>();
        java.util.Set<String> contentHashes = new java.util.HashSet<>();
        for (KnowledgeDocumentChunk candidate : candidates) {
            String chunkId = candidate.getId();
            String contentKey = StringUtils.defaultIfBlank(candidate.getContentHash(), candidate.getContent());
            if ((StringUtils.isNotBlank(chunkId) && !chunkIds.add(chunkId))
                    || (StringUtils.isNotBlank(contentKey) && !contentHashes.add(contentKey))) {
                continue;
            }
            selected.add(candidate);
        }
        return selected;
    }

    /**
     * Vector search finds the best passage, whereas a grounded answer often
     * needs its condition, exception, or conclusion immediately before/after
     * that passage.  Restore those neighbours from the same immutable
     * document version after ranking, so they do not distort recall scores.
     */
    /**
     * 以命中分块为锚点合并前后邻块，形成可供模型阅读的上下文组。
     */
    private List<KnowledgeDocumentChunk> buildContextGroups(List<KnowledgeDocumentChunk> anchors) {
        List<KnowledgeDocumentChunk> groups = new ArrayList<>();
        java.util.Set<String> includedChunkIds = new java.util.HashSet<>();
        for (KnowledgeDocumentChunk anchor : anchors) {
            if (StringUtils.isNotBlank(anchor.getId()) && includedChunkIds.contains(anchor.getId())) {
                continue;
            }
            List<KnowledgeDocumentChunk> groupChunks = new ArrayList<>();
            if (StringUtils.isBlank(anchor.getDocumentVersionId()) || anchor.getChunkIndex() == null) {
                groupChunks.add(anchor);
                groups.add(toContextGroup(anchor, groupChunks));
                continue;
            }
            try {
                List<KnowledgeDocumentChunk> neighbors = knowledgeDocumentChunkService.findNeighborChunks(
                        anchor.getDocumentVersionId(), anchor.getChunkIndex(), DEFAULT_NEIGHBOR_RADIUS);
                if (neighbors != null && !neighbors.isEmpty()) {
                    groupChunks.addAll(neighbors);
                }
            } catch (Exception e) {
                log.debug("知识片段邻块回填失败，已保留命中片段: chunkId={}", anchor.getId(), e);
            }
            if (groupChunks.isEmpty()) {
                groupChunks.add(anchor);
            }
            for (KnowledgeDocumentChunk groupChunk : groupChunks) {
                if (StringUtils.isNotBlank(groupChunk.getId())) {
                    includedChunkIds.add(groupChunk.getId());
                }
            }
            groups.add(toContextGroup(anchor, groupChunks));
        }
        return groups;
    }

    /**
     * 处理toContextGroup。
     */
    private KnowledgeDocumentChunk toContextGroup(KnowledgeDocumentChunk anchor,
                                                  List<KnowledgeDocumentChunk> groupChunks) {
        groupChunks.sort(Comparator.comparing(KnowledgeDocumentChunk::getChunkIndex,
                Comparator.nullsLast(Comparator.naturalOrder())));
        StringBuilder content = new StringBuilder();
        int tokenCount = 0;
        java.util.Set<String> contentHashes = new java.util.HashSet<>();
        for (KnowledgeDocumentChunk chunk : groupChunks) {
            String contentKey = StringUtils.defaultIfBlank(chunk.getContentHash(), chunk.getContent());
            if (StringUtils.isNotBlank(contentKey) && !contentHashes.add(contentKey)) {
                continue;
            }
            if (content.length() > 0) {
                content.append("\n\n");
            }
            content.append(chunk.getContent());
            tokenCount += chunk.getTokenCount() == null || chunk.getTokenCount() <= 0
                    ? estimateTokens(chunk.getContent()) : chunk.getTokenCount();
        }
        KnowledgeDocumentChunk group = new KnowledgeDocumentChunk();
        group.setId(anchor.getId());
        group.setKnowledgeBaseId(anchor.getKnowledgeBaseId())
                .setDocumentId(anchor.getDocumentId()).setDocumentVersionId(anchor.getDocumentVersionId())
                .setChunkIndex(anchor.getChunkIndex()).setPageNo(anchor.getPageNo())
                .setSectionPath(anchor.getSectionPath()).setContentHash(anchor.getContentHash())
                .setSimilarity(anchor.getSimilarity()).setLexicalScore(anchor.getLexicalScore())
                .setRetrievalScore(anchor.getRetrievalScore()).setContent(content.toString())
                .setTokenCount(tokenCount).setContextChunkCount(groupChunks.size())
                .setContextExpanded(groupChunks.size() > 1);
        return group;
    }

    /**
     * 按上下文 token 预算截断候选，避免挤占对话历史和回答空间。
     */
    private List<KnowledgeDocumentChunk> applyContextTokenBudget(List<KnowledgeDocumentChunk> candidates) {
        List<KnowledgeDocumentChunk> selected = new ArrayList<>();
        int usedTokens = 0;
        for (KnowledgeDocumentChunk candidate : candidates) {
            int candidateTokens = candidate.getTokenCount() == null || candidate.getTokenCount() <= 0
                    ? estimateTokens(candidate.getContent()) : candidate.getTokenCount();
            // Always preserve the best candidate: a single oversized fragment is preferable
            // to silently degrading a knowledge-grounded answer into ordinary chat.
            if (!selected.isEmpty() && usedTokens + candidateTokens > MAX_CONTEXT_TOKENS) {
                continue;
            }
            selected.add(candidate);
            usedTokens += candidateTokens;
        }
        return selected;
    }

    /**
     * 处理estimateTokens。
     */
    private int estimateTokens(String content) {
        if (StringUtils.isBlank(content)) {
            return 1;
        }
        int cjk = 0;
        int other = 0;
        for (int offset = 0; offset < content.length(); ) {
            int codePoint = content.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                cjk++;
            } else if (!Character.isWhitespace(codePoint)) {
                other++;
            }
        }
        return Math.max(1, cjk + (other + 3) / 4);
    }

    /**
     * 表示Retrieval配置。
     */
    private static class RetrievalConfig {
        private final int topK;
        private final double minSimilarity;
        private final int maxChunksPerDocument;
        private final boolean hybridEnabled;
        private final double vectorWeight;
        private final double minLexicalScore;
        private final boolean rerankEnabled;
        private final String rerankModelId;
        private final int rerankTopN;
        private final boolean strictGrounding;
        private final double authorityScore;
        private final double authorityWeight;
        private final double freshnessWeight;
        private final boolean queryRewriteEnabled;
        private final String queryRewriteModelId;

        /**
         * 创建 {@code RetrievalConfig} 实例。
         */
        private RetrievalConfig(int topK, double minSimilarity, int maxChunksPerDocument,
                                boolean hybridEnabled, double vectorWeight, double minLexicalScore,
                                boolean rerankEnabled, String rerankModelId, int rerankTopN,
                                boolean strictGrounding, double authorityScore, double authorityWeight,
                                double freshnessWeight, boolean queryRewriteEnabled,
                                String queryRewriteModelId) {
            this.topK = topK;
            this.minSimilarity = minSimilarity;
            this.maxChunksPerDocument = maxChunksPerDocument;
            this.hybridEnabled = hybridEnabled;
            this.vectorWeight = vectorWeight;
            this.minLexicalScore = minLexicalScore;
            this.rerankEnabled = rerankEnabled;
            this.rerankModelId = rerankModelId;
            this.rerankTopN = rerankTopN;
            this.strictGrounding = strictGrounding;
            this.authorityScore = authorityScore;
            this.authorityWeight = authorityWeight;
            this.freshnessWeight = freshnessWeight;
            this.queryRewriteEnabled = queryRewriteEnabled;
            this.queryRewriteModelId = queryRewriteModelId;
        }
    }

    /**
     * 表示CachedEmbedding。
     */
    private static class CachedEmbedding {
        private final List<Double> embedding;
        private final long expiresAt;

        /**
         * 创建 {@code CachedEmbedding} 实例。
         */
        private CachedEmbedding(List<Double> embedding, long expiresAt) {
            this.embedding = embedding;
            this.expiresAt = expiresAt;
        }
    }

    /**
     * 表示CachedRetrieval。
     */
    private static class CachedRetrieval {
        private final KnowledgeRetrievalResult result;
        private final long expiresAt;

        /**
         * 创建 {@code CachedRetrieval} 实例。
         */
        private CachedRetrieval(KnowledgeRetrievalResult result, long expiresAt) {
            this.result = result;
            this.expiresAt = expiresAt;
        }
    }

    /**
     * 表示Cached查询Rewrite。
     */
    private static class CachedQueryRewrite {
        private final String query;
        private final long expiresAt;

        /**
         * 创建 {@code CachedQueryRewrite} 实例。
         */
        private CachedQueryRewrite(String query, long expiresAt) {
            this.query = query;
            this.expiresAt = expiresAt;
        }
    }

    /**
     * 表示ProviderCircuit。
     */
    private static class ProviderCircuit {
        private int failures;
        private long openUntil;
    }
}
