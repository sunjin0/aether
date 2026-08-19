package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentTool;
import com.aether.agent.entity.AgentToolBinding;
import com.aether.agent.mapper.AgentToolMapper;
import com.aether.agent.service.AgentToolBindingService;
import com.aether.agent.service.AgentToolService;
import com.aether.agent.service.ToolRoutingIndexService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 工具 Service 实现
 */
@Service
public class AgentToolServiceImpl extends ServiceImpl<AgentToolMapper, AgentTool> implements AgentToolService {

    private static final String TOOLS_CACHE_KEY_PREFIX = "agent:tools:";
    private static final long TOOLS_CACHE_TTL_MINUTES = 10;

    private final RedisTemplate<String, Object> redisTemplate;
    private final AgentToolBindingService agentToolBindingService;
    private final ToolRoutingIndexService toolRoutingIndexService;

    /**
     * 创建 {@code AgentToolServiceImpl} 实例。
     */
    @Autowired
    public AgentToolServiceImpl(RedisTemplate<String, Object> redisTemplate,
                                AgentToolBindingService agentToolBindingService,
                                ToolRoutingIndexService toolRoutingIndexService) {
        this.redisTemplate = redisTemplate;
        this.agentToolBindingService = agentToolBindingService;
        this.toolRoutingIndexService = toolRoutingIndexService;
    }

    /**
     * 保存当前请求。
     */
    @Override
    public boolean save(AgentTool entity) {
        boolean saved = super.save(entity);
        if (saved) {
            evictRelatedCaches(entity.getId());
            toolRoutingIndexService.scheduleIndexTool(entity.getId());
        }
        return saved;
    }

    /**
     * 更新按Id。
     */
    @Override
    public boolean updateById(AgentTool entity) {
        boolean updated = super.updateById(entity);
        if (updated) {
            evictRelatedCaches(entity.getId());
            toolRoutingIndexService.scheduleIndexTool(entity.getId());
        }
        return updated;
    }

    /**
     * 移除按Id。
     */
    @Override
    public boolean removeById(java.io.Serializable id) {
        boolean removed = super.removeById(id);
        if (removed) {
            evictRelatedCaches(id.toString());
            toolRoutingIndexService.scheduleIndexTool(id.toString());
        }
        return removed;
    }

    /**
     * 清除与工具相关的所有Agent缓存
     */
    private void evictRelatedCaches(String toolId) {
        try {
            List<AgentToolBinding> bindings = agentToolBindingService.list(
                    Wrappers.lambdaQuery(AgentToolBinding.class)
                            .eq(AgentToolBinding::getToolId, toolId)
                            .eq(AgentToolBinding::getDeleted, false)
            );
            for (AgentToolBinding binding : bindings) {
                String cacheKey = TOOLS_CACHE_KEY_PREFIX + binding.getAgentDefinitionId();
                redisTemplate.delete(cacheKey);
            }
        } catch (Exception e) {
            // 清除缓存失败不影响主流程
        }
    }
}
