package com.aether.agent.sandbox.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 表示SandboxAudit查询DTO。
 */
@Data
@ApiModel("沙箱审计日志查询")
public class SandboxAuditQueryDto {
    @ApiModelProperty(value = "从 1 开始的页码", example = "1")
    private Long current = 1L;
    @ApiModelProperty(value = "每页数量", example = "20")
    private Long pageSize = 20L;
    @ApiModelProperty(value = "按执行状态筛选", example = "SUCCEEDED")
    private String status;
    @ApiModelProperty(value = "按沙箱模板编码筛选", example = "python-data-analysis")
    private String templateCode;
    @ApiModelProperty(value = "按请求用户 ID 筛选", example = "user-123")
    private String requesterUserId;
    @ApiModelProperty(value = "按智能体定义 ID 筛选", example = "agent-123")
    private String agentDefinitionId;
    @ApiModelProperty(value = "按审批用户 ID 筛选", example = "user-456")
    private String approverUserId;
    @ApiModelProperty(value = "按风险等级筛选", example = "MEDIUM")
    private String riskLevel;
    @ApiModelProperty(value = "创建时间起始值（含），Unix 时间戳毫秒", example = "1767225600000")
    private Long startTime;
    @ApiModelProperty(value = "创建时间结束值（含），Unix 时间戳毫秒", example = "1767312000000")
    private Long endTime;
}
