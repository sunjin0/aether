package com.aether.agent.sandbox.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 表示SandboxDecisionDTO。
 */
@Data
@ApiModel("沙箱审批决定请求")
public class SandboxDecisionDto {
    @ApiModelProperty(value = "审批决定", required = true, example = "APPROVE")
    private String decision;
    @ApiModelProperty(value = "可选审核原因", example = "The requested data export is appropriate.")
    private String reason;
}
