package com.aether.agent.service;

import com.aether.agent.entity.AgentRun;
import com.aether.agent.vo.AgentRunStatisticsVo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 运行记录 Service 接口
 */
public interface AgentRunService extends IService<AgentRun> {

    /**
     * 统计运行记录。
     *
     * @param agentDefinitionId Agent定义ID，可为空
     * @param startTime         创建时间起始时间戳，可为空
     * @param endTime           创建时间结束时间戳，可为空
     * @return 运行统计
     */
    AgentRunStatisticsVo statistics(String agentDefinitionId, Long startTime, Long endTime);
}
