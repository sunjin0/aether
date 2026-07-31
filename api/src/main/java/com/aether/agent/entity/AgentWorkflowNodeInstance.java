package com.aether.agent.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 工作流节点执行审计。文本分片不会写入此表。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_workflow_node_instance")
public class AgentWorkflowNodeInstance extends BaseEntity {
    private String instanceId;
    private String nodeId;
    private String nodeType;
    /** PENDING / RUNNING / WAITING_USER / FAILED / COMPLETED / SKIPPED */
    private String status;
    private String inputData;
    private String outputData;
    private String interactionConfig;
    private String errorMessage;
    private Integer retryCount;
    private Long startedAt;
    private Long completedAt;
}
