package com.aether.workflow.service.impl;

import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.mapper.AgentWorkflowMapper;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.local.CurrentUser;
import org.apache.commons.lang3.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 工作流 Service 实现（V0.7预留）
 */
@Service
public class AgentWorkflowServiceImpl extends ServiceImpl<AgentWorkflowMapper, AgentWorkflow> implements AgentWorkflowService {
    @Override
    public AgentWorkflow getById(java.io.Serializable id) {
        AgentWorkflow value = super.getById(id);
        String tenantId = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId");
        if (value != null && StringUtils.isNotBlank(tenantId) && !tenantId.equals(value.getTenantId())) return null;
        return value;
    }
}
