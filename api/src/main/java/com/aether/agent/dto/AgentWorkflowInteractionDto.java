package com.aether.agent.dto;

import lombok.Data;
import java.util.Map;

/** 人工节点或工具确认节点的表单答复。 */
@Data
public class AgentWorkflowInteractionDto { private Map<String, Object> answer; }
