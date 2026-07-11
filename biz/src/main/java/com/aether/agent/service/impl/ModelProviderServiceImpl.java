package com.aether.agent.service.impl;

import com.aether.agent.entity.ModelProvider;
import com.aether.agent.mapper.ModelProviderMapper;
import com.aether.agent.service.ModelProviderService;
import com.aether.entity.Option;
import com.aether.sys.entity.Dict;
import com.aether.sys.mapper.DictMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 模型供应商 Service 实现
 */
@Service
public class ModelProviderServiceImpl extends ServiceImpl<ModelProviderMapper, ModelProvider> implements ModelProviderService {
    @Autowired
    private DictMapper dictMapper;
    @Override
    public List<Option> getModelProviders() {
        List<ModelProvider> modelProviders = list(Wrappers.<ModelProvider>lambdaQuery().eq(ModelProvider::getStatus, 1));
        return modelProviders.stream().map(modelProvider -> {
            String name = modelProvider.getName();
            Dict dict = dictMapper.selectOne(Wrappers.<Dict>lambdaQuery()
                    .select(Dict::getNameCn)
                    .eq(Dict::getVal, name)
                    .last("limit 1"));
            if (dict != null)
                name = dict.getNameCn();
            return new Option(name, modelProvider.getId());
        }).collect(Collectors.toList());
    }
}
