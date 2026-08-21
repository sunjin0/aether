package com.aether.agent.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 会话记忆修正请求。
 */
@Data
public class SessionMemoryCorrectionDto {
    @ApiModelProperty(value = "替换后的记忆内容")
    private String content;

    @ApiModelProperty(value = "修正原因")
    private String reason;

    @ApiModelProperty(value = "客户端看到的记忆版本")
    private Integer memoryVersion;
}

