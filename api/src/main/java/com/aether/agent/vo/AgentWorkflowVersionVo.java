package com.aether.agent.vo;

import lombok.Data;

/** 工作流发布版本的只读快照。 */
@Data
public class AgentWorkflowVersionVo {
    private String id;
    private Integer versionNo;
    private String nodes;
    private String edges;
    private String inputSchema;
    private String outputSchema;
    private Long publishedAt;
}
