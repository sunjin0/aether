package com.aether.agent.tools;

import com.aether.agent.entity.AgentTool;
import com.aether.agent.entity.AgentToolBinding;
import com.aether.agent.service.AgentToolBindingService;
import com.aether.agent.service.AgentToolService;
import com.aether.agent.tools.core.ToolRegistry;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 负责查询 Agent 可用工具并维护工具列表缓存。
 *
 * <p>绑定关系、工具启停状态和内置工具的合并均集中在此处，调用方无需了解缓存细节。</p>
 */
@Component
public class AgentToolCatalog {
    private static final Logger log = LoggerFactory.getLogger(AgentToolCatalog.class);
    private static final String CACHE_KEY_PREFIX = "agent:tools:";
    private static final long CACHE_TTL_MINUTES = 10;

    private final AgentToolService agentToolService;
    private final AgentToolBindingService bindingService;
    private final ToolRegistry toolRegistry;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 创建 {@code AgentToolCatalog} 实例。
     */
    public AgentToolCatalog(AgentToolService agentToolService, AgentToolBindingService bindingService,
                            ToolRegistry toolRegistry,
                            @Qualifier("objectRedisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.agentToolService = agentToolService;
        this.bindingService = bindingService;
        this.toolRegistry = toolRegistry;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取RequestTools。
     */
    public List<AgentTool> getRequestTools(String agentId) {
        // 模型请求既包含 Agent 绑定的 MCP 工具，也包含平台内置工具。
        List<AgentTool> tools = new ArrayList<>(getBoundTools(agentId));
        tools.addAll(toolRegistry.getTools());
        return tools;
    }

    /**
     * 获取BoundTools。
     */
    public List<AgentTool> getBoundTools(String agentId) {
        String cacheKey = CACHE_KEY_PREFIX + agentId;
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof List) {
                return (List<AgentTool>) cached;
            }
        } catch (Exception e) {
            log.warn("读取Agent工具缓存失败: agentId={}", agentId, e);
        }
        List<AgentToolBinding> bindings = bindingService.list(Wrappers.lambdaQuery(AgentToolBinding.class)
                .eq(AgentToolBinding::getAgentDefinitionId, agentId).eq(AgentToolBinding::getStatus, 1)
                .eq(AgentToolBinding::getDeleted, false).orderByAsc(AgentToolBinding::getPriority));
        List<AgentTool> tools = new ArrayList<>();
        for (AgentToolBinding binding : bindings) {
            AgentTool tool = agentToolService.getById(binding.getToolId());
            if (tool != null && !Boolean.TRUE.equals(tool.getDeleted()) && Integer.valueOf(1).equals(tool.getStatus())) {
                tools.add(tool);
            }
        }
        try {
            redisTemplate.opsForValue().set(cacheKey, tools, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("写入Agent工具缓存失败: agentId={}", agentId, e);
        }
        return tools;
    }

    /**
     * 处理evict。
     */
    public void evict(String agentId) {
        try {
            redisTemplate.delete(CACHE_KEY_PREFIX + agentId);
        } catch (Exception e) {
            log.warn("清理Agent工具缓存失败: agentId={}", agentId, e);
        }
    }

    /**
     * 处理evict按ToolId。
     */
    public void evictByToolId(String toolId) {
        try {
            List<AgentToolBinding> bindings = bindingService.list(Wrappers.lambdaQuery(AgentToolBinding.class)
                    .eq(AgentToolBinding::getToolId, toolId).eq(AgentToolBinding::getDeleted, false));
            for (AgentToolBinding binding : bindings) {
                evict(binding.getAgentDefinitionId());
            }
        } catch (Exception e) {
            log.warn("按工具ID清理Agent工具缓存失败: toolId={}", toolId, e);
        }
    }
}
