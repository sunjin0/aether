package com.aether.workflow.dto;

import lombok.Data;

import java.util.Map;

/**
 * 手动启动流程时提交的开始节点变量。
 */
@Data
public class AgentWorkflowStartDto {
    private Map<String, Object> variables;
}
