package com.aether.agent.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

/**
 * Chat request DTO.
 */
@Data
public class AgentChatDto {

    @ApiModelProperty(value = "Agent definition ID")
    private String agentId;

    @ApiModelProperty(value = "Conversation ID")
    private String conversationId;

    @ApiModelProperty(value = "User message")
    private String message;

    @ApiModelProperty(value = "Parent interaction message ID")
    private String parentMessageId;

    @ApiModelProperty(value = "Structured interaction answer")
    private Map<String, Object> answer;

    @ApiModelProperty(value = "Enable interactive question mode")
    private Boolean interactive;

    @ApiModelProperty(value = "Enable thinking")
    private Boolean thinking;

    @ApiModelProperty(value = "Reasoning effort: low/medium/high")
    private String reasoningEffort;

    @ApiModelProperty(value = "Temporary conversation")
    private Boolean temporary;

    @ApiModelProperty(value = "Internal user ID")
    private String userId;

    /** Text recognized during the attachment-upload operation. */
    private String attachmentContent;

    /** JSON metadata returned by the attachment-upload operation. */
    private String attachments;

    /** 按平台自动装配的 Skill 编码分组的可选输入，客户端不能选择或跳过 Skill。 */
    private Map<String, Map<String, Object>> skillInputs;
}
