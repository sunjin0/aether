package com.aether.agent.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 会话消息统计 VO
 */
@Data
public class AgentMessageStatisticsVo {

    @ApiModelProperty(value = "会话ID")
    private String conversationId;

    @ApiModelProperty(value = "总消息数")
    private Long totalMessages;

    @ApiModelProperty(value = "用户消息数")
    private Long userMessages;

    @ApiModelProperty(value = "助手消息数")
    private Long assistantMessages;

    @ApiModelProperty(value = "工具消息数")
    private Long toolMessages;

    @ApiModelProperty(value = "总输入token数")
    private Long totalPromptTokens;

    @ApiModelProperty(value = "总输出token数")
    private Long totalCompletionTokens;

    @ApiModelProperty(value = "总token数")
    private Long totalTokens;

    @ApiModelProperty(value = "平均响应延迟（毫秒）")
    private Long avgLatencyMs;
}
