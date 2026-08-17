package com.aether.workflow.service;

import com.aether.workflow.vo.AgentWorkflowDeadLetterVo;
import com.aether.workflow.vo.AgentWorkflowOperationsMetricsVo;

import java.util.List;

/**
 * 提供工作流运行监控指标和死信记录查询能力。
 */
public interface AgentWorkflowOperationsService {
    /**
     * 汇总返回工作流执行、队列和失败处理指标。
     */
    AgentWorkflowOperationsMetricsVo metrics();

    /**
     * 按数量上限查询工作流异步处理产生的死信记录。
     */
    List<AgentWorkflowDeadLetterVo> deadLetters(int limit);
}
