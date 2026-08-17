package com.aether.agent.skill.service;

import com.aether.agent.entity.ModelProvider;
import com.aether.agent.service.ModelCatalogService;
import com.aether.agent.skill.dto.SkillRoutingConfigDto;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.sys.entity.Config;
import com.aether.sys.service.ConfigService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Persisted global routing settings managed from the Skill administration page.
 */
@Service
public class SkillRoutingConfigService {
    private static final String EMBEDDING_MODEL_CODE = "skill.routing.embeddingModelId";
    private static final String DISABLED = "__DISABLED__";
    private final ConfigService configService;
    private final ModelCatalogService modelCatalogService;

    /**
     * 创建 {@code SkillRoutingConfigService} 实例。
     */
    public SkillRoutingConfigService(ConfigService configService, ModelCatalogService modelCatalogService) {
        this.configService = configService;
        this.modelCatalogService = modelCatalogService;
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
     * 获取当前请求。
     */
    public SkillRoutingConfigDto get() {
        SkillRoutingConfigDto dto = new SkillRoutingConfigDto();
        dto.setEmbeddingModelId(embeddingModelId());
        return dto;
    }

    /**
     * 更新当前请求。
     */
    public void update(SkillRoutingConfigDto dto) {
        String modelId = dto == null ? null : StringUtils.trimToNull(dto.getEmbeddingModelId());
        if (modelId != null) modelCatalogService.requireAvailable(modelId, "EMBEDDING");
        Config config = configService.getOne(Wrappers.lambdaQuery(Config.class).eq(Config::getCode, EMBEDDING_MODEL_CODE).orderByDesc(Config::getCreatedAt).last("limit 1"));
        String persisted = modelId == null ? DISABLED : modelId;
        if (config == null) {
            config = new Config();
            config.setCode(EMBEDDING_MODEL_CODE);
            config.setName("Skill 路由向量模型");
            config.setRemark("Skill 渐进发现的向量召回模型目录项");
            config.setValue(persisted);
            configService.save(config);
        } else {
            config.setValue(persisted);
            configService.updateById(config);
        }
    }
}
