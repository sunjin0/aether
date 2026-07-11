package com.aether.agent.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 会话生命周期 VO
 */
@Data
public class AgentConversationLifecycleVo {

    @ApiModelProperty(value = "会话ID")
    private String conversationId;

    @ApiModelProperty(value = "创建时间")
    private Long createdAt;

    @ApiModelProperty(value = "最后活跃时间（最后一条消息时间）")
    private Long lastActiveAt;

    @ApiModelProperty(value = "关闭时间（如已关闭）")
    private Long closedAt;

    @ApiModelProperty(value = "状态：0-进行中，1-关闭，2-归档")
    private Integer status;

    @ApiModelProperty(value = "当前消息数")
    private Integer messageCount;

    @ApiModelProperty(value = "总用户消息数")
    private Long totalUserMessages;

    @ApiModelProperty(value = "总助手消息数")
    private Long totalAssistantMessages;

    @ApiModelProperty(value = "会话持续时间（毫秒，从创建到最后活跃）")
    private Long durationMs;
}
