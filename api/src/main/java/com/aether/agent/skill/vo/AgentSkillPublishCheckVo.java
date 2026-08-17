package com.aether.agent.skill.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 发布前检查结果：阻塞项必须修复，提醒项供管理员评估后继续发布。
 */
@Data
public class AgentSkillPublishCheckVo {
    private boolean ready;
    private String draftVersionId;
    private Long estimatedTokens;
    private List<String> blockers = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
}
