package com.aether.workflow.observability;

import com.aether.workflow.entity.AgentWorkflowCallbackDelivery;
import com.aether.workflow.entity.AgentWorkflowExecutionJob;
import com.aether.workflow.service.AgentWorkflowCallbackDeliveryService;
import com.aether.workflow.service.AgentWorkflowExecutionJobService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Exposes low-cardinality workflow reliability gauges for Prometheus/OTLP. */
@Component
public class WorkflowReliabilityMetrics {
    private final AgentWorkflowExecutionJobService jobService;
    private final AgentWorkflowCallbackDeliveryService callbackService;

    public WorkflowReliabilityMetrics(MeterRegistry registry,
                                      AgentWorkflowExecutionJobService jobService,
                                      AgentWorkflowCallbackDeliveryService callbackService) {
        this.jobService = jobService;
        this.callbackService = callbackService;
        registry.gauge("aether.workflow.execution.dead.letters", this, WorkflowReliabilityMetrics::executionDeadLetters);
        registry.gauge("aether.workflow.callback.dead.letters", this, WorkflowReliabilityMetrics::callbackDeadLetters);
    }

    private double executionDeadLetters() {
        return jobService.count(Wrappers.lambdaQuery(AgentWorkflowExecutionJob.class)
                .eq(AgentWorkflowExecutionJob::getStatus, "FAILED")
                .eq(AgentWorkflowExecutionJob::getDeleted, false));
    }

    private double callbackDeadLetters() {
        return callbackService.count(Wrappers.lambdaQuery(AgentWorkflowCallbackDelivery.class)
                .eq(AgentWorkflowCallbackDelivery::getStatus, "FAILED")
                .eq(AgentWorkflowCallbackDelivery::getDeleted, false));
    }
}
