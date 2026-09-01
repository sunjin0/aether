package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentMcpServer;
import com.aether.agent.mapper.AgentMcpServerMapper;
import com.aether.agent.service.AgentMcpServerService;
import com.aether.local.CurrentUser;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * MCP服务 Service 实现
 */
@Service
public class AgentMcpServerServiceImpl extends ServiceImpl<AgentMcpServerMapper, AgentMcpServer> implements AgentMcpServerService {
    @Override
    public AgentMcpServer getById(java.io.Serializable id) {
        AgentMcpServer server = super.getById(id);
        String tenantId = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId");
        if (server != null && StringUtils.isNotBlank(tenantId) && StringUtils.isNotBlank(server.getTenantId())
                && !tenantId.equals(server.getTenantId())) return null;
        return server;
    }
}
