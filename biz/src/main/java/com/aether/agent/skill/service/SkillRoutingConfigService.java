package com.aether.agent.skill.service;

import com.aether.agent.entity.ModelProvider;
import com.aether.agent.service.ModelProviderService;
import com.aether.agent.skill.dto.SkillRoutingConfigDto;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.sys.entity.Config;
import com.aether.sys.service.ConfigService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/** Persisted global routing settings managed from the Skill administration page. */
@Service
public class SkillRoutingConfigService {
    private static final String EMBEDDING_PROVIDER_CODE = "skill.routing.embeddingProviderId";
    private static final String DISABLED = "__DISABLED__";
    private final ConfigService configService;
    private final ModelProviderService providerService;

    public SkillRoutingConfigService(ConfigService configService, ModelProviderService providerService) {
        this.configService = configService; this.providerService = providerService;
    }

    public String embeddingProviderId() {
        String value = configService.getValue(EMBEDDING_PROVIDER_CODE);
        if (DISABLED.equals(value)) return null;
        return StringUtils.trimToNull(value);
    }

    public SkillRoutingConfigDto get() { SkillRoutingConfigDto dto = new SkillRoutingConfigDto(); dto.setEmbeddingProviderId(embeddingProviderId()); return dto; }

    public void update(SkillRoutingConfigDto dto) {
        String providerId = dto == null ? null : StringUtils.trimToNull(dto.getEmbeddingProviderId());
        if (providerId != null) {
            ModelProvider provider = providerService.getById(providerId);
            if (provider == null || !Integer.valueOf(1).equals(provider.getStatus()) || Boolean.TRUE.equals(provider.getDeleted())) throw new ServerException(422, I18nUtils.getMessage("skill.routing.provider.unavailable"));
        }
        Config config = configService.getOne(Wrappers.lambdaQuery(Config.class).eq(Config::getCode, EMBEDDING_PROVIDER_CODE).orderByDesc(Config::getCreatedAt).last("limit 1"));
        String persisted = providerId == null ? DISABLED : providerId;
        if (config == null) { config = new Config(); config.setCode(EMBEDDING_PROVIDER_CODE); config.setName("Skill 路由嵌入模型"); config.setRemark("Skill 渐进发现的向量召回 Provider"); config.setValue(persisted); configService.save(config); }
        else { config.setValue(persisted); configService.updateById(config); }
    }
}
