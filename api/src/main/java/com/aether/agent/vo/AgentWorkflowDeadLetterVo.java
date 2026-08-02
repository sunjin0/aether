package com.aether.agent.vo;

import lombok.Data;

/** 需要人工处理的后台执行或终态回调失败记录。 */
@Data
public class AgentWorkflowDeadLetterVo {
    private String type;
    private String id;
    private String instanceId;
    private String status;
    private Integer attemptCount;
    private String errorMessage;
    private Long occurredAt;
}
