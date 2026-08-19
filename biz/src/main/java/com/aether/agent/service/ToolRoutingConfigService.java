package com.aether.agent.service;

import com.aether.sys.entity.Config;
import com.aether.sys.service.ConfigService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Persisted global tool routing settings managed from the admin page.
 */
@Service
public class ToolRoutingConfigService {
    private static final String EMBEDDING_MODEL_CODE = "agent.tool.routing.embeddingModelId";
    private static final String TOP_K_CODE = "agent.tool.routing.topK";
    private static final String DISABLED = "__DISABLED__";
    private static final int DEFAULT_TOP_K = 8;
    private final ConfigService configService;

    /**
     * 创建 {@code ToolRoutingConfigService} 实例。
     */
    public ToolRoutingConfigService(ConfigService configService) {
        this.configService = configService;
    }

    /**
     * 处理embedding模型Id。
     */
    public String embeddingModelId() {
        String value = configService.getValue(EMBEDDING_MODEL_CODE);
        if (DISABLED.equals(value)) return null;
        return StringUtils.trimToNull(value);
    }

    /**
     * 处理topK。
     */
    public int topK() {
        String value = configService.getValue(TOP_K_CODE);
        if (StringUtils.isBlank(value)) return DEFAULT_TOP_K;
        try {
            return Math.max(1, Integer.parseInt(StringUtils.trimToEmpty(value)));
        } catch (NumberFormatException e) {
            return DEFAULT_TOP_K;
        }
    }

    /**
     * 获取当前配置。
     */
    public java.util.Map<String, Object> get() {
        java.util.Map<String, Object> dto = new java.util.LinkedHashMap<>();
        dto.put("embeddingModelId", embeddingModelId());
        dto.put("topK", topK());
        return dto;
    }

    /**
     * 更新当前配置。
     */
    public void update(String modelId, Integer topK) {
        upsert(EMBEDDING_MODEL_CODE, "Agent 工具路由向量模型", "Agent 工具按需路由的向量召回模型目录项",
                modelId == null ? DISABLED : modelId);
        if (topK != null && topK > 0) {
            upsert(TOP_K_CODE, "Agent 工具路由召回数量", "每轮按需召回的相关工具上限", String.valueOf(topK));
        }
    }

    private void upsert(String code, String name, String remark, String value) {
        Config config = configService.getOne(Wrappers.lambdaQuery(Config.class).eq(Config::getCode, code)
                .orderByDesc(Config::getCreatedAt).last("limit 1"));
        if (config == null) {
            config = new Config();
            config.setCode(code);
            config.setName(name);
            config.setRemark(remark);
            config.setValue(value);
            configService.save(config);
        } else {
            config.setValue(value);
            configService.updateById(config);
        }
    }
}