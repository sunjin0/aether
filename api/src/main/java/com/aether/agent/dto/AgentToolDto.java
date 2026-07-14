package com.aether.agent.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 工具 DTO
 */
@Data
public class AgentToolDto {

    @ApiModelProperty(value = "工具名称")
    private String name;

    @ApiModelProperty(value = "工具编码")
    private String code;

    @ApiModelProperty(value = "描述")
    private String description;

    @ApiModelProperty(value = "MCP服务ID")
    private String mcpServerId;

    @ApiModelProperty(value = "MCP tool name")
    private String mcpToolName;

    @ApiModelProperty(value = "MCP input schema JSON")
    private String mcpInputSchema;

    @ApiModelProperty(value = "超时时间（毫秒）")
    private Integer timeoutMs;

    @ApiModelProperty(value = "状态：0-禁用，1-启用")
    private Integer status;

    @ApiModelProperty(value = "备注")
    private String remark;
}
