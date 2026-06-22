package com.aether.agent.model;

import com.aether.agent.entity.ModelProvider;
import com.aether.exception.ServerException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 模型客户端工厂。
 */
@Component
public class ModelClientFactory {

    private final List<ModelClient> modelClients;

    public ModelClientFactory(List<ModelClient> modelClients) {
        this.modelClients = modelClients;
    }

    public ModelClient getClient(ModelProvider provider) {
        for (ModelClient modelClient : modelClients) {
            if (modelClient.supports(provider.getType())) {
                return modelClient;
            }
        }
        throw new ServerException(503, "不支持的模型供应商类型");
    }
}
