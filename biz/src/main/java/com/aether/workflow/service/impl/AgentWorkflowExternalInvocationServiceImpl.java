package com.aether.workflow.service.impl;

import com.aether.workflow.entity.AgentWorkflowExternalInvocation;
import com.aether.workflow.mapper.AgentWorkflowExternalInvocationMapper;
import com.aether.workflow.runtime.WorkflowSensitiveDataSanitizer;
import com.aether.workflow.service.AgentWorkflowExternalInvocationService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 在外部调用所在事务之外持久化调用意图，进程中断时保留“结果未知”的证据。
 */
@Service
public class AgentWorkflowExternalInvocationServiceImpl
        extends ServiceImpl<AgentWorkflowExternalInvocationMapper, AgentWorkflowExternalInvocation>
        implements AgentWorkflowExternalInvocationService {
    private final WorkflowSensitiveDataSanitizer sensitiveDataSanitizer;

    public AgentWorkflowExternalInvocationServiceImpl(WorkflowSensitiveDataSanitizer sensitiveDataSanitizer) {
        this.sensitiveDataSanitizer = sensitiveDataSanitizer;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public AgentWorkflowExternalInvocation recordIntent(String applicationId, String instanceId, String nodeInstanceId,
                                                         String nodeId, String invocationType, String idempotencyKey,
                                                         String method, String url, String requestData) {
        AgentWorkflowExternalInvocation existing = getOne(Wrappers.lambdaQuery(AgentWorkflowExternalInvocation.class)
                .eq(AgentWorkflowExternalInvocation::getNodeInstanceId, nodeInstanceId)
                .eq(AgentWorkflowExternalInvocation::getIdempotencyKey, idempotencyKey)
                .eq(AgentWorkflowExternalInvocation::getDeleted, false).last("FOR UPDATE"));
        if (existing != null) return existing;
        AgentWorkflowExternalInvocation value = new AgentWorkflowExternalInvocation();
        value.setApplicationId(StringUtils.defaultIfBlank(applicationId, "0"));
        value.setInstanceId(instanceId);
        value.setNodeInstanceId(nodeInstanceId);
        value.setNodeId(nodeId);
        value.setInvocationType(invocationType);
        value.setIdempotencyKey(idempotencyKey);
        value.setMethod(method);
        value.setUrl(url);
        value.setRequestData(sensitiveDataSanitizer.sanitizeJson(requestData));
        value.setStatus("RECORDED");
        save(value);
        return value;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markRunning(String id) {
        update(Wrappers.lambdaUpdate(AgentWorkflowExternalInvocation.class)
                .set(AgentWorkflowExternalInvocation::getStatus, "RUNNING")
                .set(AgentWorkflowExternalInvocation::getStartedAt, System.currentTimeMillis())
                .eq(AgentWorkflowExternalInvocation::getId, id)
                .eq(AgentWorkflowExternalInvocation::getStatus, "RECORDED"));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void complete(String id, String responseData) {
        update(Wrappers.lambdaUpdate(AgentWorkflowExternalInvocation.class)
                .set(AgentWorkflowExternalInvocation::getStatus, "COMPLETED")
                .set(AgentWorkflowExternalInvocation::getResponseData, sensitiveDataSanitizer.sanitizeJson(responseData))
                .set(AgentWorkflowExternalInvocation::getErrorMessage, null)
                .set(AgentWorkflowExternalInvocation::getCompletedAt, System.currentTimeMillis())
                .eq(AgentWorkflowExternalInvocation::getId, id)
                .in(AgentWorkflowExternalInvocation::getStatus, "RECORDED", "RUNNING"));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markUnknown(String id, String errorMessage) {
        update(Wrappers.lambdaUpdate(AgentWorkflowExternalInvocation.class)
                .set(AgentWorkflowExternalInvocation::getStatus, "UNKNOWN")
                .set(AgentWorkflowExternalInvocation::getErrorMessage, StringUtils.abbreviate(errorMessage, 2048))
                .set(AgentWorkflowExternalInvocation::getCompletedAt, System.currentTimeMillis())
                .eq(AgentWorkflowExternalInvocation::getId, id)
                .in(AgentWorkflowExternalInvocation::getStatus, "RECORDED", "RUNNING"));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markActiveAsUnknown(String instanceId, String errorMessage) {
        update(Wrappers.lambdaUpdate(AgentWorkflowExternalInvocation.class)
                .set(AgentWorkflowExternalInvocation::getStatus, "UNKNOWN")
                .set(AgentWorkflowExternalInvocation::getErrorMessage, StringUtils.abbreviate(errorMessage, 2048))
                .set(AgentWorkflowExternalInvocation::getCompletedAt, System.currentTimeMillis())
                .eq(AgentWorkflowExternalInvocation::getInstanceId, instanceId)
                .in(AgentWorkflowExternalInvocation::getStatus, "RECORDED", "RUNNING"));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void confirmSuccess(String id, String responseData) {
        update(Wrappers.lambdaUpdate(AgentWorkflowExternalInvocation.class)
                .set(AgentWorkflowExternalInvocation::getStatus, "COMPLETED")
                .set(AgentWorkflowExternalInvocation::getResponseData, sensitiveDataSanitizer.sanitizeJson(responseData))
                .set(AgentWorkflowExternalInvocation::getErrorMessage, null)
                .set(AgentWorkflowExternalInvocation::getCompletedAt, System.currentTimeMillis())
                .eq(AgentWorkflowExternalInvocation::getId, id)
                .eq(AgentWorkflowExternalInvocation::getStatus, "UNKNOWN"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetForManualRetry(String nodeInstanceId) {
        update(Wrappers.lambdaUpdate(AgentWorkflowExternalInvocation.class)
                .set(AgentWorkflowExternalInvocation::getStatus, "RECORDED")
                .set(AgentWorkflowExternalInvocation::getErrorMessage, null)
                .set(AgentWorkflowExternalInvocation::getCompletedAt, null)
                .eq(AgentWorkflowExternalInvocation::getNodeInstanceId, nodeInstanceId)
                .eq(AgentWorkflowExternalInvocation::getStatus, "UNKNOWN"));
    }

    @Override
    public List<AgentWorkflowExternalInvocation> listByInstanceId(String instanceId) {
        return list(Wrappers.lambdaQuery(AgentWorkflowExternalInvocation.class)
                .eq(AgentWorkflowExternalInvocation::getInstanceId, instanceId)
                .eq(AgentWorkflowExternalInvocation::getDeleted, false)
                .orderByAsc(AgentWorkflowExternalInvocation::getCreatedAt));
    }
}
