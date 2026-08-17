package com.aether.agent.service;

import com.aether.agent.entity.AgentToolCallLog;
import com.aether.agent.vo.AgentToolCallLogVo;
import com.aether.agent.vo.AgentToolCallStatisticsVo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * 工具调用日志 Service 接口
 */
public interface AgentToolCallLogService extends IService<AgentToolCallLog> {

    /**
     * 处理statistics。
     */
    AgentToolCallStatisticsVo statistics(AgentToolCallLogVo query);

    /**
     * 处理toolStatistics。
     */
    List<AgentToolCallStatisticsVo> toolStatistics(AgentToolCallLogVo query);

    /**
     * 处理toolStatisticsMap。
     */
    Map<String, AgentToolCallStatisticsVo> toolStatisticsMap(AgentToolCallLogVo query);
}
