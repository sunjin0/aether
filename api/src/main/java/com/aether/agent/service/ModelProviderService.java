package com.aether.agent.service;

import com.aether.agent.entity.ModelProvider;
import com.aether.entity.Option;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 模型供应商 Service 接口
 */
public interface ModelProviderService extends IService<ModelProvider> {
    /**
     * 获取模型供应商列表
     * @return 模型供应商列表
     */
    List<Option> getModelProviders();

    /**
     * 获取可用于 Embedding 的模型供应商下拉选项。
     *
     * @return Embedding 供应商选项
     */
    List<Option> getEmbeddingProviderOptions();
}
