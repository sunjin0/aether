package com.aether.agent.service.impl;

import com.aether.agent.service.AgentSessionMemoryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Removes expired, non-user-visible Session memories without retaining their content. */
@Component
public class AgentSessionMemoryRetentionScheduler {
    private final AgentSessionMemoryService memories;

    public AgentSessionMemoryRetentionScheduler(AgentSessionMemoryService memories) {
        this.memories = memories;
    }

    @Scheduled(cron = "${aether.agent.session-memory.retention-cleanup-cron:0 45 3 * * ?}")
    public void cleanupExpired() {
        memories.expireDueMemories();
    }
}
