package com.aether.knowledge.model;

import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;

/**
 * 知识库文档语义分片配置。
 *
 * <p>配置按知识库保存；未配置的历史知识库使用当前默认值，避免升级后改变既有索引行为。</p>
 */
public final class KnowledgeChunkingConfig {

    public static final String STRATEGY_SEMANTIC = "SEMANTIC";
    public static final String STRATEGY_FIXED_LENGTH = "FIXED_LENGTH";
    public static final String STRATEGY_PARAGRAPH = "PARAGRAPH";
    public static final String STRATEGY_MARKDOWN = "MARKDOWN";
    public static final int DEFAULT_MAX_CHARS = 2400;
    public static final int DEFAULT_OVERLAP_CHARS = 320;
    public static final int DEFAULT_MAX_TOKENS = 1400;
    /** text-embedding-v4 的单条上限为 8192 tokens；RAG 分片保留足够余量给标题和检索上下文。 */
    private static final int MIN_MAX_CHARS = 256;
    private static final int MAX_MAX_CHARS = 4096;
    private static final int MAX_OVERLAP_CHARS = 1024;
    private static final int MIN_MAX_TOKENS = 128;
    private static final int MAX_MAX_TOKENS = 2048;

    private final int maxChars;
    private final int overlapChars;
    private final int maxTokens;
    private final String strategy;

    private KnowledgeChunkingConfig(String strategy, int maxChars, int overlapChars, int maxTokens) {
        this.strategy = strategy;
        this.maxChars = maxChars;
        this.overlapChars = overlapChars;
        this.maxTokens = maxTokens;
    }

    public static KnowledgeChunkingConfig defaults() {
        return new KnowledgeChunkingConfig(STRATEGY_SEMANTIC,
                DEFAULT_MAX_CHARS, DEFAULT_OVERLAP_CHARS, DEFAULT_MAX_TOKENS);
    }

    /** 解析并校验配置；空值代表采用默认的语义分片策略。 */
    public static KnowledgeChunkingConfig fromJson(String value) {
        if (StringUtils.isBlank(value)) {
            return defaults();
        }
        JSONObject config = JSONObject.parseObject(value);
        if (config == null) {
            throw new IllegalArgumentException("chunking config must be an object");
        }
        String strategy = StringUtils.upperCase(StringUtils.trimToNull(config.getString("strategy")));
        if (strategy == null) {
            strategy = STRATEGY_SEMANTIC;
        }
        int maxChars = valueOrDefault(config, "maxChars", DEFAULT_MAX_CHARS);
        int overlapChars = valueOrDefault(config, "overlapChars", DEFAULT_OVERLAP_CHARS);
        int maxTokens = valueOrDefault(config, "maxTokens", DEFAULT_MAX_TOKENS);
        if (maxChars < MIN_MAX_CHARS || maxChars > MAX_MAX_CHARS
                || overlapChars < 0 || overlapChars >= maxChars
                || overlapChars > Math.min(MAX_OVERLAP_CHARS, maxChars / 2)
                || maxTokens < MIN_MAX_TOKENS || maxTokens > MAX_MAX_TOKENS
                || (!STRATEGY_SEMANTIC.equals(strategy) && !STRATEGY_FIXED_LENGTH.equals(strategy)
                && !STRATEGY_PARAGRAPH.equals(strategy) && !STRATEGY_MARKDOWN.equals(strategy))) {
            throw new IllegalArgumentException("invalid chunking limits");
        }
        return new KnowledgeChunkingConfig(strategy, maxChars, overlapChars, maxTokens);
    }

    private static int valueOrDefault(JSONObject config, String key, int defaultValue) {
        if (!config.containsKey(key) || config.get(key) == null) {
            return defaultValue;
        }
        Integer value = config.getInteger(key);
        if (value == null) {
            throw new IllegalArgumentException("chunking limit must be an integer");
        }
        return value;
    }

    /** 返回包含全部默认值的规范 JSON，便于前端展示和后续配置演进。 */
    public String toJson() {
        JSONObject config = new JSONObject();
        config.put("strategy", strategy);
        config.put("maxChars", maxChars);
        config.put("overlapChars", overlapChars);
        config.put("maxTokens", maxTokens);
        return config.toJSONString();
    }

    public int getMaxChars() {
        return maxChars;
    }

    public int getOverlapChars() {
        return overlapChars;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public String getStrategy() {
        return strategy;
    }
}
