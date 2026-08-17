package com.aether.agent.service;

import com.aether.agent.entity.AgentTaskEvent;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 定义智能体任务事件业务服务契约。
 */
public interface AgentTaskEventService extends IService<AgentTaskEvent> {
    /**
     * 处理record。
     */
    void record(String taskId, String runId, String eventType, String summary);

    /**
     * 查询按任务Id。
     */
    List<AgentTaskEvent> listByTaskId(String taskId);
}
