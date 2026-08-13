package com.aether.agent.sandbox.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sandbox_execution_approval")
public class SandboxExecutionApproval extends BaseEntity {
    private String taskId, decision, approverUserId, reason;
    private Long decidedAt;
}
