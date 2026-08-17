package com.aether.agent.service;

import com.aether.agent.entity.AgentRunStep;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 定义智能体运行Step业务服务契约。
 */
public interface AgentRunStepService extends IService<AgentRunStep> {

    /**
     * 保存IfAbsent。
     */
    boolean saveIfAbsent(AgentRunStep step);

    /**
     * 查询按运行Id。
     */
    List<AgentRunStep> listByRunId(String runId);
}
