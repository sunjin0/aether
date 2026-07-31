package com.aether.agent.dto;

import lombok.Data;

/** 工作流草稿编辑请求。 */
@Data
public class AgentWorkflowDto {
    private String name;
    private String description;
    private String nodes;
    private String edges;
    private String inputSchema;
}
