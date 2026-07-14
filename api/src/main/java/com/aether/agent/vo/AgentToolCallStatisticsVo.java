package com.aether.agent.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Tool call statistics VO.
 */
@Data
public class AgentToolCallStatisticsVo {

    @ApiModelProperty(value = "Tool ID")
    private String toolId;

    @ApiModelProperty(value = "Tool name")
    private String toolName;

    @ApiModelProperty(value = "Tool business type, such as knowledge, ops, dev")
    private String toolType;

    @ApiModelProperty(value = "Agent definition ID")
    private String agentDefinitionId;

    @ApiModelProperty(value = "Total call count")
    private Long callCount;

    @ApiModelProperty(value = "Success call count")
    private Long successCount;

    @ApiModelProperty(value = "Failed call count")
    private Long failedCount;

    @ApiModelProperty(value = "Timeout call count")
    private Long timeoutCount;

    @ApiModelProperty(value = "Security blocked call count")
    private Long securityBlockedCount;

    @ApiModelProperty(value = "Success rate")
    private Double successRate;

    private Long current;

    private Long pageSize;
}
