package com.aether.governance.service.impl;

import com.aether.governance.entity.ResourcePolicyRule;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

class ResourcePolicyServiceImplTest {
    @Test
    void conditionMustMatchContext() {
        ResourcePolicyServiceImpl service = org.mockito.Mockito.spy(new ResourcePolicyServiceImpl());
        ResourcePolicyRule rule = rule("ALLOW", "{\"region\":\"cn-east\"}");
        doReturn(Collections.singletonList(rule)).when(service).list(any());
        Map<String, Object> context = new HashMap<>();
        context.put("region", "cn-east");
        assertTrue(service.allowed("AGENT", "a1", "TOOL", "t1", "EXECUTE", context));
        context.put("region", "us-east");
        assertFalse(service.allowed("AGENT", "a1", "TOOL", "t1", "EXECUTE", context));
    }

    @Test
    void denyTakesPrecedenceOnlyWhenItsConditionMatches() {
        ResourcePolicyServiceImpl service = org.mockito.Mockito.spy(new ResourcePolicyServiceImpl());
        doReturn(Arrays.asList(rule("ALLOW", null), rule("DENY", "{\"environment\":\"prod\"}")))
                .when(service).list(any());
        Map<String, Object> context = new HashMap<>();
        context.put("environment", "prod");
        assertFalse(service.allowed("AGENT", "a1", "TOOL", "t1", "EXECUTE", context));
        context.put("environment", "dev");
        assertTrue(service.allowed("AGENT", "a1", "TOOL", "t1", "EXECUTE", context));
    }

    private ResourcePolicyRule rule(String effect, String condition) {
        ResourcePolicyRule rule = new ResourcePolicyRule();
        rule.setEffect(effect);
        rule.setConditionJson(condition);
        return rule;
    }
}
