package com.aether.execution.service.impl;

import com.aether.execution.entity.Execution;
import com.aether.execution.mapper.ExecutionMapper;
import com.aether.execution.service.ExecutionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import java.math.BigDecimal;
import com.aether.execution.vo.ExecutionTraceSummaryVo;
import com.aether.local.CurrentUser;

import java.util.UUID;

@Service
public class ExecutionServiceImpl extends ServiceImpl<ExecutionMapper, Execution> implements ExecutionService {
    @Override
    public List<Execution> listByTraceId(String traceId) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Execution> query =
                Wrappers.lambdaQuery(Execution.class)
                .eq(Execution::getTraceId, traceId)
                .eq(Execution::getDeleted, false);
        if (CurrentUser.getUser() != null) {
            String tenantId = CurrentUser.getUser().get("tenantId");
            if (tenantId != null && !tenantId.trim().isEmpty()) {
                query.eq(Execution::getTenantId, tenantId);
            }
        }
        return list(query.orderByAsc(Execution::getCreatedAt));
    }

    @Override
    public ExecutionTraceSummaryVo summarize(String traceId) {
        ExecutionTraceSummaryVo summary = new ExecutionTraceSummaryVo();
        summary.setTraceId(traceId);
        for (Execution item : listByTraceId(traceId)) {
            summary.setExecutionCount(summary.getExecutionCount() + 1);
            if ("SUCCEEDED".equals(item.getStatus())) summary.setSucceededCount(summary.getSucceededCount() + 1);
            if (item.getStatus() != null && item.getStatus().startsWith("WAITING")) summary.setWaitingCount(summary.getWaitingCount() + 1);
            if ("FAILED".equals(item.getStatus()) || "TIMED_OUT".equals(item.getStatus()) || "BLOCKED".equals(item.getStatus()))
                summary.setFailedCount(summary.getFailedCount() + 1);
            summary.setTotalPromptTokens(summary.getTotalPromptTokens() + safe(item.getPromptTokens()));
            summary.setTotalCompletionTokens(summary.getTotalCompletionTokens() + safe(item.getCompletionTokens()));
            summary.setTotalTokens(summary.getTotalTokens() + safe(item.getTotalTokens()));
            summary.setTotalDurationMs(summary.getTotalDurationMs() + safe(item.getDurationMs()));
            if (item.getEstimatedCost() != null)
                summary.setEstimatedCost(summary.getEstimatedCost().add(item.getEstimatedCost()));
        }
        return summary;
    }

    private long safe(Number value) { return value == null ? 0L : value.longValue(); }
    @Override
    public Execution start(String type, String traceId, String parentId, String actorId, String resourceId) {
        long now = System.currentTimeMillis();
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        Execution execution = new Execution().setExecutionType(type).setTraceId(traceId)
                .setParentExecutionId(parentId).setActorId(actorId).setResourceId(resourceId)
                .setStatus("RUNNING").setStartedAt(now);
        if (CurrentUser.getUser() != null) {
            execution.setTenantId(CurrentUser.getUser().get("tenantId"));
        }
        save(execution);
        return execution;
    }

    @Override
    public boolean finish(String id, String status, String errorCode, String errorMessage) {
        Execution execution = getById(id);
        if (CurrentUser.getUser() != null) {
            String tenantId = CurrentUser.getUser().get("tenantId");
            if (tenantId != null && !tenantId.trim().isEmpty()
                    && (execution == null || !tenantId.equals(execution.getTenantId()))) return false;
        }
        if (execution == null || Boolean.TRUE.equals(execution.getDeleted()) || execution.getEndedAt() != null) return false;
        if (status == null || status.trim().isEmpty()) return false;
        long now = System.currentTimeMillis();
        execution.setStatus(status).setErrorCode(errorCode).setErrorMessage(errorMessage)
                .setEndedAt(now).setDurationMs(execution.getStartedAt() == null ? null : now - execution.getStartedAt());
        return updateById(execution);
    }
}
