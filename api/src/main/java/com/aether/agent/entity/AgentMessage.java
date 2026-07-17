package com.aether.agent.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * Agent message.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("agent_message")
@ApiModel(value = "AgentMessage", description = "Agent message")
public class AgentMessage extends BaseEntity {

    @ApiModelProperty(value = "Conversation ID")
    private String conversationId;

    @ApiModelProperty(value = "Role: user/assistant/tool")
    private String role;

    @ApiModelProperty(value = "Message type: chat/interaction/answer")
    private String messageType;

    @ApiModelProperty(value = "Interaction type: group")
    private String interactionType;

    @ApiModelProperty(value = "Interaction status: pending/answered/cancelled/expired")
    private String interactionStatus;

    @ApiModelProperty(value = "Question config JSON")
    private String questionConfig;

    @ApiModelProperty(value = "Parent interaction message ID")
    private String parentMessageId;

    @ApiModelProperty(value = "Answered timestamp")
    private Long answeredAt;

    @ApiModelProperty(value = "Expiration timestamp")
    private Long expiresAt;

    @ApiModelProperty(value = "Message content")
    private String content;

    @ApiModelProperty(value = "Reasoning content")
    private String reasoningContent;

    @ApiModelProperty(value = "Tool calls JSON")
    private String toolCalls;

    @ApiModelProperty(value = "Tool call ID")
    private String toolCallId;

    @ApiModelProperty(value = "Tool result")
    private String toolResult;

    @ApiModelProperty(value = "Model")
    private String model;

    @ApiModelProperty(value = "Prompt tokens")
    private Integer promptTokens;

    @ApiModelProperty(value = "Completion tokens")
    private Integer completionTokens;

    @ApiModelProperty(value = "Total tokens")
    private Integer totalTokens;

    @ApiModelProperty(value = "Reasoning tokens")
    private Integer reasoningTokens;

    @ApiModelProperty(value = "Latency in milliseconds")
    private Integer latencyMs;

    @ApiModelProperty(value = "Edited flag")
    private Integer edited;

    @ApiModelProperty(value = "Original content")
    private String originalContent;

    @ApiModelProperty(value = "Edited timestamp")
    private Long editedAt;

    /** JSON citation snapshot used by this answer. */
    private String citations;
}
