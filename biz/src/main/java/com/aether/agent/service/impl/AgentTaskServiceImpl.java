package com.aether.agent.service.impl;
import com.aether.agent.entity.AgentTask;
import com.aether.agent.mapper.AgentTaskMapper;
import com.aether.agent.service.AgentTaskService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
@Service public class AgentTaskServiceImpl extends ServiceImpl<AgentTaskMapper, AgentTask> implements AgentTaskService {
    private static final Logger log = LoggerFactory.getLogger(AgentTaskServiceImpl.class);
    @Override public AgentTask create(String sessionId, String userId, String agentDefinitionId, String title) {
        AgentTask task = new AgentTask(); task.setSessionId(sessionId); task.setUserId(userId); task.setAgentDefinitionId(agentDefinitionId);
        task.setTitle(StringUtils.abbreviate(StringUtils.defaultIfBlank(title, "完成当前任务"), 500)); task.setStatus("QUEUED"); save(task); return task;
    }
    @Override public void updateStatus(String taskId, String status, String runId, String pauseReason) {
        if (StringUtils.isBlank(taskId) || StringUtils.isBlank(status)) return;
        AgentTask current = getById(taskId);
        if (current == null || Boolean.TRUE.equals(current.getDeleted())) return;
        String previous = StringUtils.defaultIfBlank(current.getStatus(), "QUEUED");
        if (!isAllowedTransition(previous, status)) {
            log.warn("忽略非法 Agent Task 状态转换: taskId={}, {} -> {}", taskId, previous, status);
            return;
        }
        AgentTask task = new AgentTask(); task.setId(taskId); task.setStatus(status); task.setCurrentRunId(runId); task.setPauseReason(pauseReason); updateById(task);
    }
    @Override public AgentTask nextQueued(String sessionId) {
        if (StringUtils.isBlank(sessionId)) return null;
        return getOne(Wrappers.lambdaQuery(AgentTask.class).eq(AgentTask::getSessionId, sessionId)
                .eq(AgentTask::getStatus, "QUEUED").eq(AgentTask::getDeleted, false)
                .orderByAsc(AgentTask::getCreatedAt).last("limit 1"), false);
    }
    @Override public AgentTask findActive(String sessionId) {
        if (StringUtils.isBlank(sessionId)) return null;
        return getOne(Wrappers.lambdaQuery(AgentTask.class).eq(AgentTask::getSessionId, sessionId)
                .notIn(AgentTask::getStatus, "COMPLETED", "FAILED", "CANCELLED")
                .eq(AgentTask::getDeleted, false).orderByDesc(AgentTask::getUpdatedAt).last("limit 1"), false);
    }
    private boolean isAllowedTransition(String from, String to) {
        if (from.equals(to)) return true;
        if ("QUEUED".equals(from)) return "PLANNING".equals(to) || "RUNNING".equals(to) || "FAILED".equals(to) || "CANCELLED".equals(to);
        if ("PLANNING".equals(from) || "RUNNING".equals(from))
            return "WAITING_USER".equals(to) || "WAITING_APPROVAL".equals(to) || "PAUSED".equals(to) || "COMPLETED".equals(to) || "FAILED".equals(to) || "CANCELLED".equals(to) || "RUNNING".equals(to);
        if ("WAITING_USER".equals(from) || "WAITING_APPROVAL".equals(from))
            return "RUNNING".equals(to) || "PAUSED".equals(to) || "FAILED".equals(to) || "CANCELLED".equals(to);
        if ("PAUSED".equals(from)) return "RUNNING".equals(to) || "CANCELLED".equals(to) || "FAILED".equals(to);
        return false;
    }
}
