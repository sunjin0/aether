package com.aether.agent.application.service.impl;

import com.aether.agent.application.entity.AgentApplication;
import com.aether.agent.application.mapper.AgentApplicationMapper;
import com.aether.agent.application.service.AgentApplicationService;
import com.aether.exception.ServerException;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/** 业务应用空间服务实现。 */
@Service
public class AgentApplicationServiceImpl extends ServiceImpl<AgentApplicationMapper, AgentApplication>
        implements AgentApplicationService {
    @Override
    public AgentApplication requireActive(String applicationId) {
        AgentApplication application = getById(applicationId);
        if (application == null || Boolean.TRUE.equals(application.getDeleted())) {
            throw new ServerException(404, "业务应用空间不存在");
        }
        if (!Integer.valueOf(1).equals(application.getStatus())) {
            throw new ServerException(422, "业务应用空间已停用");
        }
        return application;
    }
}
