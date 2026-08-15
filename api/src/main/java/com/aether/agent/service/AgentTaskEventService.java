package com.aether.agent.service;
import com.aether.agent.entity.AgentTaskEvent;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AgentTaskEventService extends IService<AgentTaskEvent> {
    void record(String taskId, String runId, String eventType, String summary);

    List<AgentTaskEvent> listByTaskId(String taskId);
}
