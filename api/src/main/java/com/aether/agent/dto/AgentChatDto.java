package com.aether.agent.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import com.aether.agent.entity.AgentDefinition;

import java.util.Map;

/**
 * Chat request DTO.
 */
@Data
@ApiModel("智能体聊天请求")
public class AgentChatDto {

    @ApiModelProperty(value = "智能体定义 ID", required = true, example = "agent-123")
    private String agentId;

    @ApiModelProperty(value = "会话 ID；省略时创建新会话", example = "conversation-123")
    private String conversationId;

    @ApiModelProperty(value = "新建会话的工具审批策略：ask、risky、never", example = "risky")
    private String toolApprovalPolicy;

    @ApiModelProperty(value = "用户消息", required = true, example = "Summarize the latest ticket activity.")
    private String message;

    @ApiModelProperty(value = "父交互消息 ID", example = "message-123")
    private String parentMessageId;

    @ApiModelProperty(value = "结构化交互回答", example = "{\"approved\":true}")
    private Map<String, Object> answer;

    @ApiModelProperty(value = "启用交互式提问模式", example = "true")
    private Boolean interactive;

    @ApiModelProperty(value = "启用思考", example = "true")
    private Boolean thinking;

    @ApiModelProperty(value = "推理强度：low/medium/high", example = "medium")
    private String reasoningEffort;

    @ApiModelProperty(value = "本轮知识检索模式：AUTO、ENABLED、DISABLED", example = "AUTO")
    private String retrievalMode;

    @ApiModelProperty(value = "临时会话", example = "false")
    private Boolean temporary;

    @ApiModelProperty(value = "内部用户 ID；已认证的管理调用可选", example = "user-123")
    private String userId;

    /**
     * Server-generated request correlation ID for chat latency diagnostics.
     */
    private String requestId;

    /** True only for the OpenAPI gateway after product-version authorization. */
    private Boolean openApi;

    /**
     * Server-resolved published product snapshot.  It is never populated from
     * an HTTP request and lets the OpenAPI gateway keep a published Agent
     * configuration stable while the draft Agent is edited later.
     */
    private AgentDefinition agentSnapshot;

    /**
     * Text recognized during the attachment-upload operation.
     */
    private String attachmentContent;

    /**
     * JSON metadata returned by the attachment-upload operation.
     */
    private String attachments;

    /**
     * 按平台自动装配的 Skill 编码分组的可选输入，客户端不能选择或跳过 Skill。
     */
    private Map<String, Map<String, Object>> skillInputs;

    /**
     * 本次 Deep 运行的临时秘密变量。仅接受邮件凭据，服务端不会持久化或回显其值。
     * key 为 credential_ref，value 包含 sender_email 与 smtp_authorization_code。
     */
    private Map<String, Map<String, String>> runtimeSecrets;
}
