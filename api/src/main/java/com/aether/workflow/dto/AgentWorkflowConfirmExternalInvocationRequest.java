package com.aether.workflow.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/** 管理员核对外部系统后确认调用成功时写回的响应摘要。 */
@Data
public class AgentWorkflowConfirmExternalInvocationRequest {
    @ApiModelProperty(value = "经外部系统核对后的响应摘要；不传则复用已记录的响应")
    private String responseData;
}
