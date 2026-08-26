package com.aether.agent.application.service;

import com.aether.agent.application.entity.AgentApplication;
import com.baomidou.mybatisplus.extension.service.IService;

/** 业务应用空间服务。 */
public interface AgentApplicationService extends IService<AgentApplication> {
    String PLATFORM_APPLICATION_ID = "0";

    AgentApplication requireActive(String applicationId);
}
