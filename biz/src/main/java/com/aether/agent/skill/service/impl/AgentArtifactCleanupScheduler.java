package com.aether.agent.skill.service.impl;

import com.aether.agent.skill.service.AgentArtifactService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Removes recycled generated files after their 30-day recovery window. */
@Component
public class AgentArtifactCleanupScheduler {
    private final AgentArtifactService artifactService;

    public AgentArtifactCleanupScheduler(AgentArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    @Scheduled(cron = "${aether.agent.artifact.recycle-cleanup-cron:0 20 3 * * ?}")
    public void cleanup() {
        artifactService.purgeExpiredRecycled();
    }
}
