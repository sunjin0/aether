package com.aether.agent.sandbox.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 表示SandboxRunner事件DTO。
 */
@Data
@ApiModel("沙箱运行器执行事件")
public class SandboxRunnerEventDto {
    @ApiModelProperty(value = "单调递增的事件序号", required = true, example = "3")
    private Long sequence;
    @ApiModelProperty(value = "运行器事件类型", required = true, example = "PROGRESS")
    private String eventType;
    @ApiModelProperty(value = "完成百分比", example = "50")
    private Integer progress;
    @ApiModelProperty(value = "便于阅读的事件摘要", example = "Processing uploaded input data")
    private String summary;
}
