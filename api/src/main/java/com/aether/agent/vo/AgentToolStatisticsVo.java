package com.aether.agent.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Tool management statistics VO.
 */
@Data
public class AgentToolStatisticsVo {

    @ApiModelProperty(value = "Total tool count")
    private Long totalCount;

    @ApiModelProperty(value = "Enabled tool count")
    private Long enabledCount;

    @ApiModelProperty(value = "Disabled tool count")
    private Long disabledCount;

    @ApiModelProperty(value = "Total call count")
    private Long callCount;

    @ApiModelProperty(value = "Success call count")
    private Long successCount;

    @ApiModelProperty(value = "Success rate")
    private Double successRate;
}
