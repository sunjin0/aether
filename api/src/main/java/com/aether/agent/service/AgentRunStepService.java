package com.aether.agent.service;

import com.aether.agent.entity.AgentRunStep;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AgentRunStepService extends IService<AgentRunStep> {

    boolean saveIfAbsent(AgentRunStep step);

    List<AgentRunStep> listByRunId(String runId);
}
