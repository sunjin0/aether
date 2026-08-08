package com.aether.agent.skill.vo;

import lombok.Data;

/** Skill 管理页概览指标。 */
@Data
public class AgentSkillStatisticsVo {
    private long totalCount;
    private long enabledCount;
    private long draftCount;
    private long publishedCount;
    private long boundAgentCount;
}
