package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentRunStep;
import com.aether.agent.mapper.AgentRunStepMapper;
import com.aether.agent.service.AgentRunStepService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

@Service
public class AgentRunStepServiceImpl extends ServiceImpl<AgentRunStepMapper, AgentRunStep> implements AgentRunStepService {
    private static final Logger log = LoggerFactory.getLogger(AgentRunStepServiceImpl.class);

    @Override
    public boolean saveIfAbsent(AgentRunStep step) {
        try {
            return save(step);
        } catch (DuplicateKeyException e) {
            log.info("重复事件已忽略: runId={} eventId={}", step.getRunId(), step.getEventId());
            return false;
        }
    }

    @Override
    public List<AgentRunStep> listByRunId(String runId) {
        return list(Wrappers.lambdaQuery(AgentRunStep.class)
                .eq(AgentRunStep::getRunId, runId)
                .eq(AgentRunStep::getDeleted, false)
                .orderByAsc(AgentRunStep::getOccurredAt)
                .orderByAsc(AgentRunStep::getCreatedAt));
    }
}
