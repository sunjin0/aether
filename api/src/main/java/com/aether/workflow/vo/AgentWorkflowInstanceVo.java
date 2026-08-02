package com.aether.workflow.vo;

import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.entity.AgentWorkflowNodeInstance;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class AgentWorkflowInstanceVo extends AgentWorkflowInstance {
    private String workflowName;
    private List<AgentWorkflowNodeInstance> nodes;
    private String versionNodes;
    private String versionEdges;
    private Long current;
    private Long pageSize;
}
