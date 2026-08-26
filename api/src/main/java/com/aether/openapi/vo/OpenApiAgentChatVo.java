package com.aether.openapi.vo;

import lombok.Data;

/** 外部 Agent 问答安全响应，不包含工具参数、请求头、推理内容或原始模型响应。 */
@Data
public class OpenApiAgentChatVo {
    private String conversationId;
    private String answer;
    private String citations;
    private String runId;
    private String traceId;
    /** pending 时由业务系统按标准审批/转人工流程处理，禁止猜测自然语言状态。 */
    private String interactionStatus;
    private String interactionType;
}
