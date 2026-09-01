package com.aether.evaluation.service.impl;

import com.aether.evaluation.entity.EvaluationPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class EvaluationPolicyServiceImplTest {
    @Test
    void missingPolicyKeepsExistingPublishBehavior() {
        EvaluationPolicyServiceImpl service = spy(new EvaluationPolicyServiceImpl());
        doReturn(null).when(service).getOne(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(false));

        assertTrue(service.allowedToPublish("WORKFLOW", "workflow-1"));
    }

    @Test
    void requiredPolicyNeedsPassedScoreAboveThreshold() {
        EvaluationPolicyServiceImpl service = spy(new EvaluationPolicyServiceImpl());
        EvaluationPolicy policy = new EvaluationPolicy();
        policy.setRequired(true); policy.setMinimumScore(80); policy.setLastStatus("PASSED"); policy.setLastScore(79);
        doReturn(policy).when(service).getOne(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(false));

        assertFalse(service.allowedToPublish("WORKFLOW", "workflow-1"));
        policy.setLastScore(80);
        assertTrue(service.allowedToPublish("WORKFLOW", "workflow-1"));
    }

    @Test
    void failedEvaluationCannotPassRequiredPolicy() {
        EvaluationPolicyServiceImpl service = spy(new EvaluationPolicyServiceImpl());
        EvaluationPolicy policy = new EvaluationPolicy();
        policy.setRequired(true); policy.setMinimumScore(0); policy.setLastStatus("FAILED"); policy.setLastScore(100);
        doReturn(policy).when(service).getOne(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(false));

        assertFalse(service.allowedToPublish("AGENT", "agent-1"));
    }
}
