package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentTool;
import com.aether.agent.entity.AgentToolCallLog;
import com.aether.agent.mapper.AgentToolCallLogMapper;
import com.aether.agent.service.AgentToolCallLogService;
import com.aether.agent.service.AgentToolService;
import com.aether.agent.vo.AgentToolCallLogVo;
import com.aether.agent.vo.AgentToolCallStatisticsVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool call log service implementation.
 */
@Service
public class AgentToolCallLogServiceImpl extends ServiceImpl<AgentToolCallLogMapper, AgentToolCallLog> implements AgentToolCallLogService {

    private final AgentToolService agentToolService;

    @Autowired
    public AgentToolCallLogServiceImpl(AgentToolService agentToolService) {
        this.agentToolService = agentToolService;
    }

    @Override
    public AgentToolCallStatisticsVo statistics(AgentToolCallLogVo query) {
        List<AgentToolCallLog> logs = filteredLogs(query);
        AgentToolCallStatisticsVo statistics = new AgentToolCallStatisticsVo();
        fillCounts(statistics, logs);
        return statistics;
    }

    @Override
    public List<AgentToolCallStatisticsVo> toolStatistics(AgentToolCallLogVo query) {
        List<AgentToolCallStatisticsVo> result = new ArrayList<>(toolStatisticsMap(query).values());
        result.sort(Comparator.comparing(AgentToolCallStatisticsVo::getCallCount, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    @Override
    public Map<String, AgentToolCallStatisticsVo> toolStatisticsMap(AgentToolCallLogVo query) {
        List<AgentToolCallLog> logs = filteredLogs(query);
        Map<String, AgentTool> tools = loadTools(logs);
        Map<String, AgentToolCallStatisticsVo> grouped = new LinkedHashMap<>();
        for (AgentToolCallLog log : logs) {
            String key = statisticKey(log);
            AgentToolCallStatisticsVo item = grouped.get(key);
            if (item == null) {
                item = new AgentToolCallStatisticsVo();
                item.setToolId(log.getToolId());
                item.setToolName(log.getToolName());
                item.setAgentDefinitionId(log.getAgentDefinitionId());
                AgentTool tool = StringUtils.isBlank(log.getToolId()) ? null : tools.get(log.getToolId());
                if (tool != null) {
                    item.setToolName(StringUtils.defaultIfBlank(log.getToolName(), tool.getName()));
                    item.setToolType(tool.getToolType());
                }
                grouped.put(key, item);
            }
            addLog(item, log);
        }
        return grouped;
    }

    private List<AgentToolCallLog> filteredLogs(AgentToolCallLogVo query) {
        List<String> typeToolIds = null;
        if (query != null && StringUtils.isNotBlank(query.getToolType())) {
            List<AgentTool> tools = agentToolService.list(Wrappers.lambdaQuery(AgentTool.class)
                    .eq(AgentTool::getToolType, query.getToolType())
                    .eq(AgentTool::getDeleted, false));
            typeToolIds = new ArrayList<>();
            for (AgentTool tool : tools) {
                typeToolIds.add(tool.getId());
            }
            if (typeToolIds.isEmpty()) {
                return new ArrayList<>();
            }
        }

        LambdaQueryWrapper<AgentToolCallLog> wrapper = Wrappers.lambdaQuery(AgentToolCallLog.class)
                .eq(query != null && StringUtils.isNotBlank(query.getRunId()), AgentToolCallLog::getRunId, query == null ? null : query.getRunId())
                .eq(query != null && StringUtils.isNotBlank(query.getToolId()), AgentToolCallLog::getToolId, query == null ? null : query.getToolId())
                .like(query != null && StringUtils.isNotBlank(query.getToolName()), AgentToolCallLog::getToolName, query == null ? null : query.getToolName())
                .eq(query != null && StringUtils.isNotBlank(query.getAgentDefinitionId()), AgentToolCallLog::getAgentDefinitionId, query == null ? null : query.getAgentDefinitionId())
                .eq(query != null && query.getStatus() != null, AgentToolCallLog::getStatus, query == null ? null : query.getStatus())
                .ge(query != null && query.getStartTime() != null, AgentToolCallLog::getCreatedAt, query == null ? null : query.getStartTime())
                .le(query != null && query.getEndTime() != null, AgentToolCallLog::getCreatedAt, query == null ? null : query.getEndTime())
                .eq(AgentToolCallLog::getDeleted, false)
                .orderByDesc(AgentToolCallLog::getCreatedAt);
        if (typeToolIds != null) {
            wrapper.in(AgentToolCallLog::getToolId, typeToolIds);
        }
        return list(wrapper);
    }

    private Map<String, AgentTool> loadTools(List<AgentToolCallLog> logs) {
        List<String> toolIds = new ArrayList<>();
        for (AgentToolCallLog log : logs) {
            if (StringUtils.isNotBlank(log.getToolId()) && !toolIds.contains(log.getToolId())) {
                toolIds.add(log.getToolId());
            }
        }
        if (toolIds.isEmpty()) {
            return new HashMap<>();
        }
        List<AgentTool> tools = agentToolService.list(Wrappers.lambdaQuery(AgentTool.class)
                .in(AgentTool::getId, toolIds)
                .eq(AgentTool::getDeleted, false));
        Map<String, AgentTool> map = new HashMap<>();
        for (AgentTool tool : tools) {
            map.put(tool.getId(), tool);
        }
        return map;
    }

    private String statisticKey(AgentToolCallLog log) {
        if (StringUtils.isNotBlank(log.getToolId())) {
            return log.getToolId();
        }
        return StringUtils.defaultIfBlank(log.getToolName(), "unknown");
    }

    private void fillCounts(AgentToolCallStatisticsVo statistics, List<AgentToolCallLog> logs) {
        for (AgentToolCallLog log : logs) {
            addLog(statistics, log);
        }
    }

    private void addLog(AgentToolCallStatisticsVo statistics, AgentToolCallLog log) {
        statistics.setCallCount(defaultLong(statistics.getCallCount()) + 1);
        if (Integer.valueOf(0).equals(log.getStatus())) {
            statistics.setSuccessCount(defaultLong(statistics.getSuccessCount()) + 1);
        } else if (Integer.valueOf(2).equals(log.getStatus())) {
            statistics.setTimeoutCount(defaultLong(statistics.getTimeoutCount()) + 1);
        } else if (Integer.valueOf(3).equals(log.getStatus())) {
            statistics.setSecurityBlockedCount(defaultLong(statistics.getSecurityBlockedCount()) + 1);
        } else {
            statistics.setFailedCount(defaultLong(statistics.getFailedCount()) + 1);
        }
        statistics.setSuccessRate(successRate(statistics.getSuccessCount(), statistics.getCallCount()));
    }

    private Long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private Double successRate(Long successCount, Long callCount) {
        if (callCount == null || callCount == 0) {
            return 0D;
        }
        return BigDecimal.valueOf(defaultLong(successCount))
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(callCount), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
