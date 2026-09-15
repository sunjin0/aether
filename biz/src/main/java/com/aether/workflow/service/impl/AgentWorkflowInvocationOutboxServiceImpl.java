package com.aether.workflow.service.impl;

import com.aether.workflow.entity.AgentWorkflowInvocationOutbox;
import com.aether.workflow.mapper.AgentWorkflowInvocationOutboxMapper;
import com.aether.workflow.service.AgentWorkflowInvocationOutboxService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentWorkflowInvocationOutboxServiceImpl
        extends ServiceImpl<AgentWorkflowInvocationOutboxMapper, AgentWorkflowInvocationOutbox>
        implements AgentWorkflowInvocationOutboxService {
    @Override
    public boolean enqueue(AgentWorkflowInvocationOutbox event) {
        if (event == null || StringUtils.isAnyBlank(event.getInvocationId(), event.getWorkflowInstanceId(), event.getEventType())) return false;
        // 补偿扫描每 5 秒运行一次；同一实例/事件/状态版本只允许一条记录。
        // 先读后写可避免正常重复扫描触发数据库异常日志，唯一索引仍负责并发兜底。
        AgentWorkflowInvocationOutbox existing = getOne(Wrappers.lambdaQuery(AgentWorkflowInvocationOutbox.class)
                .eq(AgentWorkflowInvocationOutbox::getWorkflowInstanceId, event.getWorkflowInstanceId())
                .eq(AgentWorkflowInvocationOutbox::getEventType, event.getEventType())
                .eq(AgentWorkflowInvocationOutbox::getStateVersion, event.getStateVersion())
                .eq(AgentWorkflowInvocationOutbox::getDeleted, false)
                .last("LIMIT 1"));
        if (existing != null) return false;
        try {
            return save(event);
        } catch (Exception exception) {
            // 不同 MyBatis/Druid 配置会将 PostgreSQL 唯一键冲突包装成不同异常类型。
            // 只有确认是唯一键冲突时吞掉，其他数据库故障继续抛出，交给调度器暴露。
            String message = exception.getMessage();
            if (message != null && (message.contains("duplicate key") || message.contains("unique constraint")
                    || message.contains("agent_workflow_invocation_outbox_uk"))) return false;
            throw exception;
        }
    }

    @Override
    public List<AgentWorkflowInvocationOutbox> claimPending(long now, int limit) {
        List<AgentWorkflowInvocationOutbox> candidates = list(Wrappers.lambdaQuery(AgentWorkflowInvocationOutbox.class)
                .in(AgentWorkflowInvocationOutbox::getStatus, "PENDING", "RETRYING", "DELIVERING")
                .le(AgentWorkflowInvocationOutbox::getNextAttemptAt, now)
                .eq(AgentWorkflowInvocationOutbox::getDeleted, false)
                .orderByAsc(AgentWorkflowInvocationOutbox::getCreatedAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 500))));
        List<AgentWorkflowInvocationOutbox> claimed = new ArrayList<>();
        for (AgentWorkflowInvocationOutbox candidate : candidates) {
            boolean ok = update(Wrappers.lambdaUpdate(AgentWorkflowInvocationOutbox.class)
                    .set(AgentWorkflowInvocationOutbox::getStatus, "DELIVERING")
                    .setSql("attempt_count = COALESCE(attempt_count, 0) + 1")
                    .set(AgentWorkflowInvocationOutbox::getNextAttemptAt, now + 60000L)
                    .eq(AgentWorkflowInvocationOutbox::getId, candidate.getId())
                    // A DELIVERING row is reclaimable only after its lease has
                    // expired.  Without this predicate two workers can claim
                    // the same row during the first delivery attempt.
                    .and(wrapper -> wrapper
                            .in(AgentWorkflowInvocationOutbox::getStatus, "PENDING", "RETRYING")
                            .or(nested -> nested.eq(AgentWorkflowInvocationOutbox::getStatus, "DELIVERING")
                                    .le(AgentWorkflowInvocationOutbox::getNextAttemptAt, now))));
            if (ok) {
                AgentWorkflowInvocationOutbox claimedEvent = getById(candidate.getId());
                if (claimedEvent != null) claimed.add(claimedEvent);
            }
        }
        return claimed;
    }
}
