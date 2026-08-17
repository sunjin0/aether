package com.aether.agent.service;

import com.aether.agent.entity.ModelCatalog;
import com.aether.entity.Option;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 定义模型Catalog业务服务契约。
 */
public interface ModelCatalogService extends IService<ModelCatalog> {
    /**
     * 获取Options。
     */
    List<Option> getOptions(String capability);

    /**
     * Validates and normalizes a catalog entry before it is persisted.
     */
    void validateForSave(ModelCatalog model);

    /**
     * 处理requireAvailable。
     */
    ModelCatalog requireAvailable(String id, String capability);

    /**
     * Returns an enabled provider connection configured for this catalog model.
     */
    com.aether.agent.entity.ModelProvider resolveProvider(String id, String capability);
}
