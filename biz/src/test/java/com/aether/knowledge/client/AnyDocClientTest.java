package com.aether.knowledge.client;

import com.aether.agent.dto.DeepAgentConfig;
import com.aether.agent.service.DelegationTokenService;
import org.junit.jupiter.api.Test;

class AnyDocClientTest {

    @Test
    void clientCanBeInstantiatedWithoutServiceUrl() {
        AnyDocClient client = new AnyDocClient("", 1000, tokenService());
        assert !client.isEnabled();
    }

    private DelegationTokenService tokenService() {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setMcpDelegationSecret("test-delegation-secret");
        return new DelegationTokenService(config);
    }
}
