package com.aether.openapi.service;

import com.aether.agent.product.entity.AgentProductProfile;
import com.aether.exception.ServerException;
import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrustedContextServiceTest {
    private final TrustedContextService service = new TrustedContextService();

    @Test
    void acceptsDeclaredTypedContextAndKeepsImmutableIdentity() {
        AgentProductProfile product = product("{\"customerId\":{\"type\":\"string\",\"immutable\":true},\"channel\":{\"type\":\"string\"}}");
        Map<String, Object> initial = new LinkedHashMap<String, Object>();
        initial.put("customerId", "c-1"); initial.put("channel", "web");
        String stored = service.merge(product, null, initial);
        assertEquals("c-1", service.read(stored).get("customerId"));

        assertThrows(ServerException.class, () -> service.merge(product, stored, Collections.<String, Object>singletonMap("customerId", "c-2")));
    }

    @Test
    void rejectsUndeclaredContext() {
        assertThrows(ServerException.class, () -> service.merge(product(null), null, Collections.<String, Object>singletonMap("customerId", "c-1")));
    }

    private AgentProductProfile product(String allowedContextKeys) {
        AgentProductProfile product = new AgentProductProfile();
        product.setAllowedContextKeys(allowedContextKeys);
        return product;
    }
}
