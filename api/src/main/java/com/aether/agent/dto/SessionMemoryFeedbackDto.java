package com.aether.agent.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 会话记忆反馈请求。
 */
@Data
@ApiModel("会话记忆反馈请求")
public class SessionMemoryFeedbackDto {
    @ApiModelProperty(value = "记忆ID")
    private String memoryId;

    @ApiModelProperty(value = "客户端看到的记忆版本")
    private Integer memoryVersion;

    @ApiModelProperty(value = "反馈：ACCURATE、INACCURATE、EXPIRED")
    private String verdict;

    @ApiModelProperty(value = "原因；INACCURATE 必填")
    private String reason;
}
