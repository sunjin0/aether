package com.aether.agent.service;

import com.aether.agent.dto.AgentWorkflowScheduleTriggerDto;
import com.aether.agent.entity.AgentWorkflowScheduleTrigger;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AgentWorkflowScheduleTriggerService extends IService<AgentWorkflowScheduleTrigger> {
    AgentWorkflowScheduleTrigger create(AgentWorkflowScheduleTriggerDto dto);
    boolean setEnabled(String id, boolean enabled);
    /** 由调度 Worker 通过数据库租约触发，返回 false 表示已被其他实例领取。 */
    boolean triggerDue(String id, long scheduledAt);
}
