package com.aether.agent.sandbox.vo;

import lombok.Data;

/** Audit-safe approval projection; reasons are already length-bounded and redacted by the service. */
@Data
public class SandboxApprovalVo {
    private String decision, approverUserId, reason;
    private Long decidedAt;
}
