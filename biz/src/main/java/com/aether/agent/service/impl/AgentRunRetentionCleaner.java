package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentRun;
import com.aether.agent.service.AgentRunService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 按配置的保留周期定时清理过期的运行审计快照。 */
@Component
public class AgentRunRetentionCleaner {
    private final AgentRunService runService;

    @Value("${agent.audit.retention-days:0}")
    private int retentionDays;

    public AgentRunRetentionCleaner(AgentRunService runService) {
        this.runService = runService;
    }

    @Scheduled(fixedDelayString = "${agent.audit.retention-scan-ms:3600000}", initialDelay = 120000L)
    public void purgeExpiredRuns() {
        if (retentionDays <= 0) return;
        long cutoff = System.currentTimeMillis() - retentionDays * 86400000L;
        runService.remove(Wrappers.lambdaQuery(AgentRun.class)
                .eq(AgentRun::getDeleted, false)
                .lt(AgentRun::getCreatedAt, cutoff));
    }
}
