package com.aether.agent.skill.service;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class SkillRouteDecision {
    private String skillVersionId;
    private String reason;
    private Double confidence;
    private List<Map<String, Object>> candidates = new ArrayList<>();
    public boolean isMatched() { return skillVersionId != null; }
}
