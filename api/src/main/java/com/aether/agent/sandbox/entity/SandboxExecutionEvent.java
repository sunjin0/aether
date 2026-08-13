package com.aether.agent.sandbox.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sandbox_execution_event")
public class SandboxExecutionEvent extends BaseEntity {
    private String taskId, eventType, status, summary, subjectSha256;
    private Long sequence, occurredAt;
    private Integer progress;
}
