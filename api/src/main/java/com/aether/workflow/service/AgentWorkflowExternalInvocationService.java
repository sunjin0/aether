package com.aether.workflow.service;

import com.aether.workflow.entity.AgentWorkflowExternalInvocation;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AgentWorkflowExternalInvocationService extends IService<AgentWorkflowExternalInvocation> {
    AgentWorkflowExternalInvocation recordIntent(String applicationId, String instanceId, String nodeInstanceId,
                                                 String nodeId, String invocationType, String idempotencyKey,
                                                 String method, String url, String requestData);

    void markRunning(String id);

    void complete(String id, String responseData);

    void markUnknown(String id, String errorMessage);
    void markActiveAsUnknown(String instanceId, String errorMessage);

    void confirmSuccess(String id, String responseData);

    void resetForManualRetry(String nodeInstanceId);

    List<AgentWorkflowExternalInvocation> listByInstanceId(String instanceId);
}
