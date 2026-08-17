package com.aether.workflow.service;

import com.aether.workflow.dto.AgentWorkflowScheduleTriggerDto;
import com.aether.workflow.entity.AgentWorkflowScheduleTrigger;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 定义智能体工作流调度Trigger业务服务契约。
 */
public interface AgentWorkflowScheduleTriggerService extends IService<AgentWorkflowScheduleTrigger> {
    /**
     * 创建当前请求。
     */
    AgentWorkflowScheduleTrigger create(AgentWorkflowScheduleTriggerDto dto);

    /**
     * 更新当前请求。
     */
    boolean update(String id, AgentWorkflowScheduleTriggerDto dto);

    /**
     * 处理setEnabled。
     */
    boolean setEnabled(String id, boolean enabled);

    /**
     * 删除当前请求。
     */
    boolean delete(String id);

    /**
     * 由调度 Worker 通过数据库租约触发，返回 false 表示已被其他实例领取。
     */
    boolean triggerDue(String id, long scheduledAt);
}
