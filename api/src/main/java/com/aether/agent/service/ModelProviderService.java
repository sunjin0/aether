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
}
