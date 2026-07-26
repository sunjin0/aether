package com.aether.agent.vo;

import lombok.Data;

/** 文件上传识别完成后返回给聊天客户端的附件信息。 */
@Data
public class AgentChatAttachmentVo {
    private String fileName;
    private String contentType;
    private Long size;
    private String objectKey;
    private String extractedContent;
}
