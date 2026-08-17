package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentSessionMemory;
import com.aether.agent.mapper.AgentSessionMemoryMapper;
import com.aether.agent.service.AgentSessionMemoryService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 实现智能体会话Memory业务服务。
 */
@Service
public class AgentSessionMemoryServiceImpl extends ServiceImpl<AgentSessionMemoryMapper, AgentSessionMemory>
        implements AgentSessionMemoryService {
    private static final int CONTENT_LIMIT = 2000;

    /**
     * 处理record任务Conclusion。
     */
    @Override
    public void recordTaskConclusion(String sessionId, String taskId, String runId, String content) {
        if (StringUtils.isBlank(sessionId) || StringUtils.isBlank(content)) return;
        AgentSessionMemory memory = new AgentSessionMemory();
        String sanitized = sanitize(content);
        memory.setSessionId(sessionId);
        memory.setMemoryType("TASK_CONCLUSION");
        memory.setContent(sanitized);
        memory.setSummary(StringUtils.abbreviate(sanitized, 500));
        memory.setSourceTaskId(taskId);
        memory.setSourceRunId(runId);
        memory.setImportance(80);
        memory.setSensitivityLevel("NORMAL");
        memory.setMemoryVersion(1);
        save(memory);
    }

    /**
     * 查询Injectable。
     */
    @Override
    public List<AgentSessionMemory> listInjectable(String sessionId, int limit) {
        if (StringUtils.isBlank(sessionId) || limit <= 0) return java.util.Collections.emptyList();
        long now = System.currentTimeMillis();
        return list(Wrappers.lambdaQuery(AgentSessionMemory.class)
                .eq(AgentSessionMemory::getSessionId, sessionId)
                .eq(AgentSessionMemory::getDeleted, false)
                .and(query -> query.isNull(AgentSessionMemory::getExpiresAt).or()
                        .gt(AgentSessionMemory::getExpiresAt, now))
                .orderByDesc(AgentSessionMemory::getImportance)
                .orderByDesc(AgentSessionMemory::getCreatedAt)
                .last("limit " + Math.min(limit, 12)));
    }

    /**
     * 处理expireDueMemories。
     */
    @Override
    public int expireDueMemories() {
        long now = System.currentTimeMillis();
        AgentSessionMemory update = new AgentSessionMemory();
        update.setDeleted(true);
        return baseMapper.update(update, Wrappers.lambdaUpdate(AgentSessionMemory.class)
                .eq(AgentSessionMemory::getDeleted, false)
                .isNotNull(AgentSessionMemory::getExpiresAt)
                .le(AgentSessionMemory::getExpiresAt, now));
    }

    /**
     * 清理敏感信息当前请求。
     */
    private String sanitize(String value) {
        String compact = value.replaceAll("(?i)(password|passwd|secret|api[_-]?key|access[_-]?token|private[_-]?key)\\s*[:=]\\s*\\S+", "$1=[REDACTED]");
        return StringUtils.abbreviate(compact, CONTENT_LIMIT);
    }
}
