package com.aether.agent.service.impl;

import com.aether.agent.entity.ModelProvider;
import com.aether.agent.mapper.ModelProviderMapper;
import com.aether.agent.service.ModelProviderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 模型供应商 Service 实现
 */
@Service
public class ModelProviderServiceImpl extends ServiceImpl<ModelProviderMapper, ModelProvider> implements ModelProviderService {
}
