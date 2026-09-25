package com.aether.agent.sandbox.entity;

import com.aether.entity.AccountOwnedEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表示SandboxExecutionApproval。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sandbox_execution_approval")
public class SandboxExecutionApproval extends AccountOwnedEntity {
    private String taskId, decision, approverUserId, reason;
    private Long decidedAt;
}
