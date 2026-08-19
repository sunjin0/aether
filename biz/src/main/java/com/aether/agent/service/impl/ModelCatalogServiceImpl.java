package com.aether.agent.service.impl;

import com.aether.agent.entity.ModelCatalog;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.mapper.ModelCatalogMapper;
import com.aether.agent.service.ModelCatalogService;
import com.aether.agent.service.ModelProviderService;
import com.aether.entity.Option;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.beans.BeanUtils;

import java.util.List;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 实现模型Catalog业务服务。
 */
@Service
public class ModelCatalogServiceImpl extends ServiceImpl<ModelCatalogMapper, ModelCatalog> implements ModelCatalogService {
    private static final Set<String> SUPPORTED_CAPABILITIES = new java.util.HashSet<>(Arrays.asList("CHAT", "VIDEO", "AUDIO", "MULTIMODAL", "EMBEDDING", "RERANK"));
    private final ModelProviderService providerService;

    /**
     * 创建 {@code ModelCatalogServiceImpl} 实例。
     */
    public ModelCatalogServiceImpl(ModelProviderService providerService) {
        this.providerService = providerService;
    }

    /**
     * 获取Options。
     */
    @Override
    public List<Option> getOptions(String capability) {
        return list(Wrappers.lambdaQuery(ModelCatalog.class).eq(ModelCatalog::getStatus, 1)
                .eq(ModelCatalog::getDeleted, false).orderByAsc(ModelCatalog::getSortNum)).stream()
                .filter(item -> supports(item, capability)).filter(item -> providerAvailable(item.getProviderId()))
                .map(item -> new Option(item.getName() + "（" + providerName(item.getProviderId()) + "）", item.getId())).collect(Collectors.toList());
    }

    /**
     * 校验用于保存。
     */
    @Override
    public void validateForSave(ModelCatalog model) {
        if (model == null || StringUtils.isBlank(model.getProviderId()) || StringUtils.isBlank(model.getName()) || StringUtils.isBlank(model.getCapabilities())) {
            throw new ServerException(400, I18nUtils.getMessage("agent.model.catalog.required"));
        }
        if (!providerAvailable(model.getProviderId())) {
            throw new ServerException(400, I18nUtils.getMessage("agent.model.catalog.capability.invalid"));
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String capability : model.getCapabilities().split(",")) {
            String value = StringUtils.upperCase(StringUtils.trimToEmpty(capability));
            if (StringUtils.isBlank(value) || !SUPPORTED_CAPABILITIES.contains(value)) {
                throw new ServerException(400, I18nUtils.getMessage("agent.model.catalog.capability.invalid"));
            }
            normalized.add(value);
        }
        model.setName(StringUtils.trim(model.getName()));
        model.setCapabilities(String.join(",", normalized));
    }

    /**
     * 处理requireAvailable。
     */
    @Override
    public ModelCatalog requireAvailable(String id, String capability) {
        ModelCatalog item = getById(id);
        if (item == null || Boolean.TRUE.equals(item.getDeleted()) || !Integer.valueOf(1).equals(item.getStatus())
                || !supports(item, capability) || !providerAvailable(item.getProviderId()))
            throw new ServerException(400, I18nUtils.getMessage("agent.model.catalog.capability.invalid"));
        return item;
    }

    /**
     * 解析Provider。
     */
    @Override
    public ModelProvider resolveProvider(String id, String capability) {
        ModelCatalog item = requireAvailable(id, capability);
        ModelProvider source = providerService.getById(item.getProviderId());
        ModelProvider resolved = new ModelProvider();
        BeanUtils.copyProperties(source, resolved);
        resolved.setDefaultModel(item.getName());
        if (item.getContextWindow() != null) resolved.setContextWindow(item.getContextWindow());
        if (StringUtils.isNotBlank(item.getEndpointOverride())) resolved.setApiBaseUrl(item.getEndpointOverride());
        return resolved;
    }

    /**
     * 处理supports。
     */
    private boolean supports(ModelCatalog item, String capability) {
        if (StringUtils.isBlank(capability)) return true;
        for (String value : capability.split(",")) {
            if (("," + StringUtils.upperCase(StringUtils.defaultString(item.getCapabilities())) + ",")
                    .contains("," + StringUtils.upperCase(value.trim()) + ",")) return true;
        }
        return false;
    }

    /**
     * 处理providerAvailable。
     */
    private boolean providerAvailable(String id) {
        ModelProvider p = providerService.getById(id);
        return p != null && !Boolean.TRUE.equals(p.getDeleted()) && Integer.valueOf(1).equals(p.getStatus());
    }

    /**
     * 处理providerName。
     */
    private String providerName(String id) {
        ModelProvider p = providerService.getById(id);
        return p == null ? "-" : p.getName();
    }
}
