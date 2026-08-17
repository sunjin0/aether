package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentToolBinding;
import com.aether.agent.mapper.AgentToolBindingMapper;
import com.aether.agent.service.AgentToolBindingService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 工具绑定 Service 实现
 */
@Service
public class AgentToolBindingServiceImpl extends ServiceImpl<AgentToolBindingMapper, AgentToolBinding> implements AgentToolBindingService {

    private static final String TOOLS_CACHE_KEY_PREFIX = "agent:tools:";

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 创建 {@code AgentToolBindingServiceImpl} 实例。
     */
    @Autowired
    public AgentToolBindingServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 保存当前请求。
     */
    @Override
    public boolean save(AgentToolBinding entity) {
        boolean saved = super.save(entity);
        if (saved) {
            evictAgentCache(entity.getAgentDefinitionId());
        }
        return saved;
    }

    /**
     * 更新按Id。
     */
    @Override
    public boolean updateById(AgentToolBinding entity) {
        boolean updated = super.updateById(entity);
        if (updated) {
            evictAgentCache(entity.getAgentDefinitionId());
        }
        return updated;
    }

    /**
     * 移除按Id。
     */
    @Override
    public boolean removeById(java.io.Serializable id) {
        // 先获取绑定信息，再删除
        AgentToolBinding binding = getById(id);
        boolean removed = super.removeById(id);
        if (removed && binding != null) {
            evictAgentCache(binding.getAgentDefinitionId());
        }
        return removed;
    }

    /**
     * 清除指定Agent的工具缓存
     */
    private void evictAgentCache(String agentId) {
        try {
            String cacheKey = TOOLS_CACHE_KEY_PREFIX + agentId;
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            // 清除缓存失败不影响主流程
        }
    }
}
