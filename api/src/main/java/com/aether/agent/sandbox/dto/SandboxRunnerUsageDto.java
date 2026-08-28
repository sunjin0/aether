package com.aether.agent.sandbox.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 表示SandboxRunnerUsageDTO。
 */
@Data
@ApiModel("沙箱运行器资源使用报告")
public class SandboxRunnerUsageDto {
    @ApiModelProperty(value = "已用实际时间（毫秒）", example = "1250")
    private Long wallMillis;
    @ApiModelProperty(value = "CPU 时间（毫秒）", example = "830")
    private Long cpuMillis;
    @ApiModelProperty(value = "驻留内存峰值（字节）", example = "134217728")
    private Long maxRssBytes;
    @ApiModelProperty(value = "写入输出的字节数", example = "4096")
    private Long outputBytes;
    @ApiModelProperty(value = "进程退出码", example = "0")
    private Integer exitCode;
}
