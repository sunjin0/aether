package com.aether.agent.sandbox.entity;

import com.aether.entity.AccountOwnedEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表示SandboxExecution事件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sandbox_execution_event")
public class SandboxExecutionEvent extends AccountOwnedEntity {
    private String taskId, eventType, status, summary, subjectSha256;
    private Long sequence, occurredAt;
    private Integer progress;
}
