package com.aether.workflow.service;

import com.aether.workflow.dto.AgentWorkflowInteractionDto;
import com.aether.workflow.dto.AgentWorkflowBusinessStartDto;
import com.aether.workflow.dto.AgentWorkflowEventDto;
import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.vo.AgentWorkflowInstanceVo;

import java.util.Map;

/**
 * 工作流实例状态机。
 */
public interface AgentWorkflowExecutionService {
    /**
     * 启动处理流程。
     */
    AgentWorkflowInstance start(String workflowId, Map<String, Object> variables, String userId);

    /**
     * 由业务系统通过服务账号启动；支持业务关联和幂等。
     */
    AgentWorkflowInstance startBusiness(String workflowId, AgentWorkflowBusinessStartDto dto, String userId);

    /**
     * 查询工作流实例详情，并校验当前用户对实例的访问权限。
     */
    AgentWorkflowInstanceVo detail(String instanceId, String userId);

    /**
     * 提交人工交互节点的答复，并继续推进工作流实例。
     */
    void answer(String instanceId, AgentWorkflowInteractionDto dto, String userId);

    /** 提交业务事件并恢复等待事件节点。 */
    void signalEvent(String instanceId, String eventType, AgentWorkflowEventDto dto, String userId);
    void timeoutEvent(String instanceId, String nodeId);

    /** 按业务空间、事件类型和关联键唤醒匹配的等待事件节点，返回已恢复的实例数。 */
    int signalEventByType(String applicationId, String eventType, AgentWorkflowEventDto dto, String userId);

    /** 由调度器恢复到期的延时节点。 */
    void resumeDelay(String instanceId, String nodeId);

    /** 人工确认结果未知的外部调用已成功，并将已确认的结果回填后继续执行。 */
    void confirmExternalInvocation(String instanceId, String invocationId, String responseData, String userId);

    /** 人工明确同意后，使用原幂等键重新尝试结果未知的外部调用。 */
    void retryExternalInvocation(String instanceId, String invocationId, String userId);

    /**
     * 重试处于可重试状态的工作流实例。
     */
    void retry(String instanceId, String userId);

    /** 显式重试当前失败节点，nodeId 用于防止管理端误操作其他节点。 */
    void retryNode(String instanceId, String nodeId, String userId);

    /**
     * 使用原始开始变量重新发起手动实例；业务关联实例禁止回放以避免外部副作用重复发生。
     */
    AgentWorkflowInstance replay(String instanceId, String userId);

    /**
     * 由持久化后台任务调用，推进处于 RUNNING 状态的实例。
     */
    void executePending(String instanceId);

    /**
     * 后台执行基础设施连续失败达到上限时，将实例收敛为可见失败终态。
     */
    void failPendingExecution(String instanceId, String errorMessage);

    /**
     * 终止尚未结束的工作流实例。
     */
    void terminate(String instanceId, String userId);

    /**
     * 运行中更新流程共享状态中的开始变量字段。
     */
    void updateVariables(String instanceId, Map<String, Object> variables, String userId);

    /**
     * 是否为可管理其他用户流程实例的平台 root 管理员。
     */
    boolean isAdministrator(String userId);
}
