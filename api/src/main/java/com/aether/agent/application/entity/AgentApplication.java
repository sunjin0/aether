package com.aether.agent.application.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 内部业务系统在 Agent 中台中的隔离空间。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_application")
public class AgentApplication extends BaseEntity {
    /** 所属租户；为空时兼容历史单租户数据。 */
    private String tenantId;
    /** 稳定、可用于开放 API 的业务应用编码。 */
    private String code;
    private String name;
    private String description;
    /** 0-停用，1-启用。 */
    private Integer status;
    /** 每小时应用级 Agent 调用上限；0 表示不限制。 */
    private Integer maxAgentCallsPerHour;
    /** 每小时应用级工作流启动上限；0 表示不限制。 */
    private Integer maxWorkflowStartsPerHour;
}
