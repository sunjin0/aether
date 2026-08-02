package com.aether.agent.service;

import com.aether.agent.dto.AgentWorkflowInteractionDto;
import com.aether.agent.dto.AgentWorkflowBusinessStartDto;
import com.aether.agent.entity.AgentWorkflowInstance;
import com.aether.agent.vo.AgentWorkflowInstanceVo;
import java.util.Map;

/** 工作流实例状态机。 */
public interface AgentWorkflowExecutionService {
    AgentWorkflowInstance start(String workflowId, Map<String, Object> variables, String userId);
    /** 由业务系统通过服务账号启动；支持业务关联和幂等。 */
    AgentWorkflowInstance startBusiness(String workflowId, AgentWorkflowBusinessStartDto dto, String userId);
    AgentWorkflowInstanceVo detail(String instanceId, String userId);
    void answer(String instanceId, AgentWorkflowInteractionDto dto, String userId);
    void retry(String instanceId, String userId);
    /** 使用原始开始变量重新发起手动实例；业务关联实例禁止回放以避免外部副作用重复发生。 */
    AgentWorkflowInstance replay(String instanceId, String userId);
    /** 由持久化后台任务调用，推进处于 RUNNING 状态的实例。 */
    void executePending(String instanceId);
    /** 后台执行基础设施连续失败达到上限时，将实例收敛为可见失败终态。 */
    void failPendingExecution(String instanceId, String errorMessage);
    void terminate(String instanceId, String userId);
    /** 运行中更新流程共享状态中的开始变量字段。 */
    void updateVariables(String instanceId, Map<String, Object> variables, String userId);
    /** 是否为可管理其他用户流程实例的平台 root 管理员。 */
    boolean isAdministrator(String userId);
}
