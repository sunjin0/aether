package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentSession;
import com.aether.agent.mapper.AgentSessionMapper;
import com.aether.agent.service.AgentSessionService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class AgentSessionServiceImpl extends ServiceImpl<AgentSessionMapper, AgentSession> implements AgentSessionService {
    @Override
    public synchronized AgentSession getOrCreate(String conversationId, String userId, String agentDefinitionId) {
        AgentSession existing = getOne(Wrappers.lambdaQuery(AgentSession.class)
                .eq(AgentSession::getConversationId, conversationId)
                .eq(AgentSession::getDeleted, false));
        if (existing != null) {
            if (StringUtils.isBlank(existing.getGraphThreadId())) {
                AgentSession update = new AgentSession();
                update.setId(existing.getId());
                update.setGraphThreadId(existing.getId());
                updateById(update);
                existing.setGraphThreadId(existing.getId());
            }
            touch(existing.getId());
            return existing;
        }
        AgentSession session = new AgentSession();
        session.setConversationId(conversationId);
        session.setUserId(userId);
        session.setAgentDefinitionId(agentDefinitionId);
        session.setStatus("ACTIVE");
        session.setMemoryVersion(0);
        session.setLastActiveAt(System.currentTimeMillis());
        try {
            save(session);
        } catch (DuplicateKeyException duplicate) {
            // 多实例同时首次收到同一会话消息时，数据库唯一键是最终裁决；冲突后复用胜出的 Session。
            AgentSession winner = getOne(Wrappers.lambdaQuery(AgentSession.class)
                    .eq(AgentSession::getConversationId, conversationId)
                    .eq(AgentSession::getDeleted, false));
            if (winner == null) throw duplicate;
            if (StringUtils.isBlank(winner.getGraphThreadId())) {
                AgentSession update = new AgentSession();
                update.setId(winner.getId());
                update.setGraphThreadId(winner.getId());
                updateById(update);
                winner.setGraphThreadId(winner.getId());
            }
            touch(winner.getId());
            return winner;
        }
        session.setGraphThreadId(session.getId());
        updateById(session);
        return session;
    }

    @Override
    public void touch(String sessionId) {
        if (sessionId == null) return;
        AgentSession update = new AgentSession();
        update.setId(sessionId);
        update.setLastActiveAt(System.currentTimeMillis());
        updateById(update);
    }

    @Override
    public boolean claimTask(String sessionId, String taskId) {
        if (StringUtils.isBlank(sessionId) || StringUtils.isBlank(taskId)) return false;
        return update(null, Wrappers.lambdaUpdate(AgentSession.class)
                .set(AgentSession::getActiveTaskId, taskId)
                .set(AgentSession::getStatus, "ACTIVE")
                .set(AgentSession::getLastActiveAt, System.currentTimeMillis())
                .eq(AgentSession::getId, sessionId)
                .isNull(AgentSession::getActiveTaskId));
    }

    @Override
    public void updateTaskState(String sessionId, String taskId, String taskStatus) {
        if (StringUtils.isBlank(sessionId) || StringUtils.isBlank(taskId)) return;
        long now = System.currentTimeMillis();
        if ("COMPLETED".equals(taskStatus) || "FAILED".equals(taskStatus) || "CANCELLED".equals(taskStatus)) {
            update(null, Wrappers.lambdaUpdate(AgentSession.class)
                    .set(AgentSession::getStatus, "ACTIVE")
                    .set(AgentSession::getActiveTaskId, null)
                    .set(AgentSession::getLastActiveAt, now)
                    .eq(AgentSession::getId, sessionId)
                    .eq(AgentSession::getActiveTaskId, taskId));
            return;
        }
        String sessionStatus = "WAITING_USER".equals(taskStatus) || "WAITING_APPROVAL".equals(taskStatus)
                || "PAUSED".equals(taskStatus) ? taskStatus : "ACTIVE";
        update(null, Wrappers.lambdaUpdate(AgentSession.class)
                .set(AgentSession::getStatus, sessionStatus)
                .set(AgentSession::getLastActiveAt, now)
                .eq(AgentSession::getId, sessionId)
                .eq(AgentSession::getActiveTaskId, taskId));
    }
}
