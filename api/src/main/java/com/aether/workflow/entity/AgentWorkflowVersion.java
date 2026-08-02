package com.aether.workflow.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 已发布工作流的不可变快照。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_workflow_version")
public class AgentWorkflowVersion extends BaseEntity {
    private String workflowId;
    private Integer versionNo;
    private String nodes;
    private String edges;
    private String inputSchema;
    private String outputSchema;
    private Long publishedAt;
}
