package com.aether.agent.service.impl;
import com.aether.agent.entity.AgentTaskEvent;
import com.aether.agent.mapper.AgentTaskEventMapper;
import com.aether.agent.service.AgentTaskEventService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentTaskEventServiceImpl extends ServiceImpl<AgentTaskEventMapper, AgentTaskEvent>
        implements AgentTaskEventService {
    @Override
    public void record(String taskId, String runId, String eventType, String summary) {
        if (StringUtils.isBlank(taskId)) return;
        AgentTaskEvent event = new AgentTaskEvent();
        event.setTaskId(taskId);
        event.setRunId(runId);
        event.setEventType(eventType);
        event.setSummary(StringUtils.abbreviate(StringUtils.defaultString(summary), 1000));
        event.setOccurredAt(System.currentTimeMillis());
        save(event);
    }

    @Override
    public List<AgentTaskEvent> listByTaskId(String taskId) {
        return list(Wrappers.lambdaQuery(AgentTaskEvent.class)
                .eq(AgentTaskEvent::getTaskId, taskId)
                .eq(AgentTaskEvent::getDeleted, false)
                .orderByAsc(AgentTaskEvent::getOccurredAt));
    }
}
