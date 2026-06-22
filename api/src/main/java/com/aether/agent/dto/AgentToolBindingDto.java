package com.aether.agent.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 工具绑定 DTO
 */
@Data
public class AgentToolBindingDto {

    @ApiModelProperty(value = "关联工具ID")
    private String toolId;

    @ApiModelProperty(value = "调用优先级")
    private Integer priority;

    @ApiModelProperty(value = "状态：0-禁用，1-启用")
    private Integer status;
}
