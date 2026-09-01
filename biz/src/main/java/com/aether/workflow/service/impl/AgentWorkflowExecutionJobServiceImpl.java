package com.aether.workflow.service.impl;

import com.aether.workflow.entity.AgentWorkflowExecutionJob;
import com.aether.workflow.mapper.AgentWorkflowExecutionJobMapper;
import com.aether.workflow.service.AgentWorkflowExecutionJobService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import com.aether.local.CurrentUser;
import org.apache.commons.lang3.StringUtils;

/**
 * 实现智能体工作流ExecutionJob业务服务。
 */
@Service
public class AgentWorkflowExecutionJobServiceImpl
        extends ServiceImpl<AgentWorkflowExecutionJobMapper, AgentWorkflowExecutionJob>
        implements AgentWorkflowExecutionJobService {
    @Override
    public AgentWorkflowExecutionJob getById(java.io.Serializable id) {
        AgentWorkflowExecutionJob value = super.getById(id);
        String tenantId = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId");
        if (value != null && StringUtils.isNotBlank(tenantId) && !tenantId.equals(value.getTenantId())) return null;
        return value;
    }
}
