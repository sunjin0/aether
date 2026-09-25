package com.aether.workflow.service.impl;

import com.aether.workflow.entity.AgentWorkflowVersion;
import com.aether.workflow.mapper.AgentWorkflowVersionMapper;
import com.aether.workflow.service.AgentWorkflowVersionService;
import com.aether.local.CurrentUser;
import org.apache.commons.lang3.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 实现智能体工作流Version业务服务。
 */
@Service
public class AgentWorkflowVersionServiceImpl extends ServiceImpl<AgentWorkflowVersionMapper, AgentWorkflowVersion> implements AgentWorkflowVersionService {
    @Override
    public AgentWorkflowVersion getById(java.io.Serializable id) {
        AgentWorkflowVersion version = super.getById(id);
        String accountId = CurrentUser.dataOwnerId();
        if (version != null && StringUtils.isNotBlank(accountId) && !accountId.equals(version.getCreatedBy())) return null;
        return version;
    }
}
