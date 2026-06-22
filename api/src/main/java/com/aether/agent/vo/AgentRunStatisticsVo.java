package com.aether.agent.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 运行统计 VO
 */
@Data
public class AgentRunStatisticsVo {

    @ApiModelProperty(value = "Agent定义ID")
    private String agentDefinitionId;

    @ApiModelProperty(value = "总调用次数")
    private Long totalCalls;

    @ApiModelProperty(value = "成功次数")
    private Long successCalls;

    @ApiModelProperty(value = "失败次数")
    private Long failedCalls;

    @ApiModelProperty(value = "超时次数")
    private Long timeoutCalls;

    @ApiModelProperty(value = "总输入token数")
    private Long totalPromptTokens;

    @ApiModelProperty(value = "总输出token数")
    private Long totalCompletionTokens;

    @ApiModelProperty(value = "总token数")
    private Long totalTokens;

    @ApiModelProperty(value = "平均耗时（毫秒）")
    private Long avgLatencyMs;

    @ApiModelProperty(value = "错误率")
    private Double errorRate;
}
