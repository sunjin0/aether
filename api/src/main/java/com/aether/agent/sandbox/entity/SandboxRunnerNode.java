package com.aether.agent.sandbox.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Observed Runner identity; task leases remain the execution authority.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sandbox_runner_node")
public class SandboxRunnerNode extends BaseEntity {
    private String runnerId, currentTaskId;
    private Long firstSeenAt, lastSeenAt, lastClaimedAt;
}
