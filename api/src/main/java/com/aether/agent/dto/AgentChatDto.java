package com.aether.agent.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 聊天请求 DTO
 */
@Data
public class AgentChatDto {

    @ApiModelProperty(value = "Agent定义ID")
    private String agentId;

    @ApiModelProperty(value = "会话ID（可选，首次对话不传）")
    private String conversationId;

    @ApiModelProperty(value = "消息内容")
    private String message;

    @ApiModelProperty(value = "用户ID（内部传递，不从接口传入）")
    private String userId;
}
