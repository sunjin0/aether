package com.aether.workflow.service.impl;

import com.aether.workflow.entity.AgentWorkflowCallbackDelivery;
import com.aether.workflow.entity.AgentWorkflowExecutionJob;
import com.aether.workflow.mapper.AgentWorkflowMetricsMapper;
import com.aether.workflow.service.AgentWorkflowCallbackDeliveryService;
import com.aether.workflow.service.AgentWorkflowExecutionJobService;
import com.aether.workflow.service.AgentWorkflowOperationsService;
import com.aether.workflow.vo.AgentWorkflowDeadLetterVo;
import com.aether.workflow.vo.AgentWorkflowOperationsMetricsVo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class AgentWorkflowOperationsServiceImpl implements AgentWorkflowOperationsService {
    private final AgentWorkflowMetricsMapper metricsMapper;
    private final AgentWorkflowCallbackDeliveryService callbackService;
    private final AgentWorkflowExecutionJobService jobService;

    public AgentWorkflowOperationsServiceImpl(AgentWorkflowMetricsMapper metricsMapper,
                                                AgentWorkflowCallbackDeliveryService callbackService,
                                                AgentWorkflowExecutionJobService jobService) {
        this.metricsMapper = metricsMapper; this.callbackService = callbackService; this.jobService = jobService;
    }

    @Override
    public AgentWorkflowOperationsMetricsVo metrics() {
        Map<String, Object> instances = metricsMapper.instanceMetrics();
        Map<String, Object> nodes = metricsMapper.nodeMetrics();
        Map<String, Object> callbacks = metricsMapper.callbackMetrics();
        Map<String, Object> jobs = metricsMapper.executionMetrics();
        AgentWorkflowOperationsMetricsVo value = new AgentWorkflowOperationsMetricsVo();
        long total = number(instances, "total"); long completed = number(instances, "completed");
        value.setTotalInstances(total); value.setCompletedInstances(completed); value.setFailedInstances(number(instances, "failed"));
        value.setWaitingUserInstances(number(instances, "waiting"));
        value.setCompletionRate(total == 0 ? 0D : completed * 100D / total);
        value.setAverageCompletedDurationMs(decimal(instances, "completed_duration"));
        value.setAverageWaitingUserDurationMs(decimal(instances, "waiting_duration"));
        value.setAverageNodeDurationMs(decimal(nodes, "node_duration"));
        value.setMcpFailedCount(number(nodes, "mcp_failed")); value.setCallbackFailedCount(number(callbacks, "callback_failed"));
        value.setExecutionDeadLetterCount(number(jobs, "execution_dead_letter"));
        return value;
    }

    @Override
    public List<AgentWorkflowDeadLetterVo> deadLetters(int limit) {
        int size = Math.max(1, Math.min(limit, 200));
        List<AgentWorkflowDeadLetterVo> result = new ArrayList<AgentWorkflowDeadLetterVo>();
        for (AgentWorkflowExecutionJob job : jobService.list(Wrappers.lambdaQuery(AgentWorkflowExecutionJob.class)
                .eq(AgentWorkflowExecutionJob::getStatus, "FAILED").eq(AgentWorkflowExecutionJob::getDeleted, false)
                .orderByDesc(AgentWorkflowExecutionJob::getUpdatedAt).last("LIMIT " + size))) {
            AgentWorkflowDeadLetterVo value = new AgentWorkflowDeadLetterVo(); value.setType("EXECUTION_JOB"); value.setId(job.getId());
            value.setInstanceId(job.getInstanceId()); value.setStatus(job.getStatus()); value.setAttemptCount(job.getAttemptCount());
            value.setErrorMessage(job.getErrorMessage()); value.setOccurredAt(job.getUpdatedAt()); result.add(value);
        }
        for (AgentWorkflowCallbackDelivery delivery : callbackService.list(Wrappers.lambdaQuery(AgentWorkflowCallbackDelivery.class)
                .eq(AgentWorkflowCallbackDelivery::getStatus, "FAILED").eq(AgentWorkflowCallbackDelivery::getDeleted, false)
                .orderByDesc(AgentWorkflowCallbackDelivery::getUpdatedAt).last("LIMIT " + size))) {
            AgentWorkflowDeadLetterVo value = new AgentWorkflowDeadLetterVo(); value.setType("CALLBACK"); value.setId(delivery.getId());
            value.setInstanceId(delivery.getInstanceId()); value.setStatus(delivery.getStatus()); value.setAttemptCount(delivery.getAttemptCount());
            value.setErrorMessage(delivery.getErrorMessage()); value.setOccurredAt(delivery.getUpdatedAt()); result.add(value);
        }
        Collections.sort(result, new Comparator<AgentWorkflowDeadLetterVo>() { @Override public int compare(AgentWorkflowDeadLetterVo a, AgentWorkflowDeadLetterVo b) {
            return Long.compare(b.getOccurredAt() == null ? 0L : b.getOccurredAt(), a.getOccurredAt() == null ? 0L : a.getOccurredAt());
        }});
        return result.size() <= size ? result : result.subList(0, size);
    }

    private long number(Map<String, Object> map, String key) { Object value = map == null ? null : map.get(key); return value instanceof Number ? ((Number) value).longValue() : 0L; }
    private double decimal(Map<String, Object> map, String key) { Object value = map == null ? null : map.get(key); return value instanceof Number ? ((Number) value).doubleValue() : 0D; }
}
