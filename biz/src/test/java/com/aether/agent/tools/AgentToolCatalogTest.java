package com.aether.agent.tools;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.AgentToolBindingService;
import com.aether.agent.service.AgentToolService;
import com.aether.agent.tools.core.ToolRegistry;
import com.aether.workflow.entity.AgentWorkflowCapability;
import com.aether.workflow.service.AgentWorkflowCapabilityService;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 锁住下发给模型的工作流工具声明。
 *
 * <p>这类声明以前零覆盖，也正是最容易悄悄漂移的地方：schema 改了但缓存没提版本，
 * 模型会按旧参数表调用，而且最多十分钟不生效。
 */
class AgentToolCatalogTest {

    @Test
    @SuppressWarnings("unchecked")
    void workflowListAdvertisesIncludeCompletedAsAnOptionalParameter() {
        AgentWorkflowCapability capability = new AgentWorkflowCapability();
        capability.setId("cap-1");
        capability.setCapabilityCode("file_analyse");
        AgentToolCatalog catalog = catalog(capability);

        List<AgentTool> tools = catalog.getBoundTools("agent-1");

        AgentTool list = null;
        for (AgentTool tool : tools) if ("workflow_list".equals(tool.getCode())) list = tool;
        assertNotNull(list, "workflow_list 应当随能力一起下发");
        JSONObject schema = JSONObject.parseObject(list.getParametersSchema());
        assertTrue(schema.getJSONObject("properties").containsKey("includeCompleted"));
        // 参数必须可选：传了才有意义，模型不该为了列清单被迫编一个值。
        assertTrue(schema.getJSONArray("required").isEmpty(), schema.toJSONString());
        // 描述要写清「含已结束的」，否则模型仍会以为这里只有在跑的。
        assertTrue(list.getDescription().contains("已结束"), list.getDescription());
    }

    @Test
    @SuppressWarnings("unchecked")
    void workflowListAdvertisesTheQueryParameters() {
        AgentWorkflowCapability capability = new AgentWorkflowCapability();
        capability.setId("cap-1");
        capability.setCapabilityCode("file_analyse");
        AgentToolCatalog catalog = catalog(capability);

        List<AgentTool> tools = catalog.getBoundTools("agent-1");

        AgentTool list = null;
        for (AgentTool tool : tools) if ("workflow_list".equals(tool.getCode())) list = tool;
        assertNotNull(list);
        JSONObject schema = JSONObject.parseObject(list.getParametersSchema());
        JSONObject properties = schema.getJSONObject("properties");
        for (String name : new String[]{"invocationId", "state", "createdAfter", "createdBefore",
                "capabilityCode", "includeCompleted", "includeOutput", "current", "pageSize"}) {
            assertTrue(properties.containsKey(name), "缺少参数: " + name);
        }
        // state 是枚举：模型会自己发明 "completed" / "succeeded" 这类词，
        // 而 applyStateFilter 只认 running/finished/all，剩下的值一律当「不筛」静默放过。
        assertEquals(new JSONArray().fluentAdd("running").fluentAdd("finished").fluentAdd("all"),
                properties.getJSONObject("state").getJSONArray("enum"));
        // 筛选参数一个都不能是必填：不带参数时走的是「在跑优先」的精选视图。
        assertTrue(schema.getJSONArray("required").isEmpty(), schema.toJSONString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void schemaChangesAreShippedByBumpingTheCacheVersion() {
        AgentWorkflowCapability capability = new AgentWorkflowCapability();
        capability.setId("cap-1");
        capability.setCapabilityCode("file_analyse");
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);
        AgentDefinitionService definitions = mock(AgentDefinitionService.class);
        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-1");
        agent.setApplicationId("app-1");
        when(definitions.getById(anyString())).thenReturn(agent);
        AgentToolBindingService bindings = mock(AgentToolBindingService.class);
        when(bindings.list(any())).thenReturn(Collections.emptyList());
        AgentWorkflowCapabilityService capabilityService = mock(AgentWorkflowCapabilityService.class);
        when(capabilityService.listEnabledForAgent(anyString(), anyString()))
                .thenReturn(Collections.singletonList(capability));
        AgentToolCatalog catalog = new AgentToolCatalog(mock(AgentToolService.class), bindings,
                mock(ToolRegistry.class), redis, definitions, capabilityService);

        catalog.getBoundTools("agent-1");

        // workflowCacheMatches 不比对 schema，提版本号是参数表改动的唯一失效手段；
        // 漏提的话新参数最多十分钟才下发，而模型按旧参数表调用不会报错、只会答错。
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(values).set(key.capture(), any(), anyLong(), any());
        assertTrue(key.getValue().startsWith("agent:tools:v7:"), key.getValue());
    }

    @Test
    void workflowListIsNotOfferedWhenTheAgentHasNoCapability() {
        AgentToolCatalog catalog = catalog();

        List<AgentTool> tools = catalog.getBoundTools("agent-1");

        for (AgentTool tool : tools) {
            assertTrue(!"workflow_list".equals(tool.getCode()), "没有工作流能力时不该下发工作流工具");
        }
        assertEquals(0, tools.size());
    }

    @SuppressWarnings("unchecked")
    private AgentToolCatalog catalog(AgentWorkflowCapability... capabilities) {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);
        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-1");
        agent.setApplicationId("app-1");
        AgentDefinitionService definitions = mock(AgentDefinitionService.class);
        when(definitions.getById(anyString())).thenReturn(agent);
        AgentToolBindingService bindings = mock(AgentToolBindingService.class);
        when(bindings.list(any())).thenReturn(Collections.emptyList());
        AgentWorkflowCapabilityService capabilityService = mock(AgentWorkflowCapabilityService.class);
        when(capabilityService.listEnabledForAgent(anyString(), anyString()))
                .thenReturn(java.util.Arrays.asList(capabilities));
        return new AgentToolCatalog(mock(AgentToolService.class), bindings, mock(ToolRegistry.class),
                redis, definitions, capabilityService);
    }
}
