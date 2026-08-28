package com.aether.openapi.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import java.util.Map;

/** 外部业务系统的同步 Agent 问答请求。 */
@Data
@ApiModel("OpenAPI 同步智能体聊天请求")
public class OpenApiAgentChatDto {
    @ApiModelProperty(value = "已发布智能体编码", required = true, example = "support-assistant")
    private String agentCode;
    @ApiModelProperty(value = "已发布产品编码", required = true, example = "support-chat")
    private String productCode;
    @ApiModelProperty(value = "已有会话 ID；省略时创建会话", example = "conversation-123")
    private String conversationId;
    @ApiModelProperty(value = "调用方业务记录 ID", example = "CASE-2048")
    private String businessId;
    @ApiModelProperty(value = "用于安全重试提交的稳定唯一键", example = "case-CASE-2048-message-1")
    private String idempotencyKey;
    @ApiModelProperty(value = "发送给智能体的用户输入", required = true, example = "Summarize the customer issue and recommend the next step.")
    private String input;
    @ApiModelProperty(value = "产品允许的上下文数据", example = "{\"customerTier\":\"gold\",\"locale\":\"en-US\"}")
    private Map<String, Object> context;
    /** Last sequence observed by the caller; required for concurrent channels. */
    @ApiModelProperty(value = "调用方观察到的最后交互序号；仅协调并发渠道时需要", example = "12")
    private Long expectedLastSequence;
}
