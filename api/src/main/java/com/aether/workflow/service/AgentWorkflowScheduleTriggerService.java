package com.aether.workflow.service;

import com.aether.workflow.dto.AgentWorkflowScheduleTriggerDto;
import com.aether.workflow.entity.AgentWorkflowScheduleTrigger;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AgentWorkflowScheduleTriggerService extends IService<AgentWorkflowScheduleTrigger> {
    AgentWorkflowScheduleTrigger create(AgentWorkflowScheduleTriggerDto dto);
    boolean update(String id, AgentWorkflowScheduleTriggerDto dto);
    boolean setEnabled(String id, boolean enabled);
    boolean delete(String id);
    /** 由调度 Worker 通过数据库租约触发，返回 false 表示已被其他实例领取。 */
    boolean triggerDue(String id, long scheduledAt);
}
