package com.aether.agent.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 工具 DTO
 */
@Data
@ApiModel("智能体工具创建或更新请求")
public class AgentToolDto {

    @ApiModelProperty(value = "工具名称")
    private String name;

    @ApiModelProperty(value = "工具编码")
    private String code;

    @ApiModelProperty(value = "描述")
    private String description;

    @ApiModelProperty(value = "系统图标库名称")
    private String icon;

    @ApiModelProperty(value = "工具业务类型，例如 knowledge、ops、dev")
    private String toolType;

    @ApiModelProperty(value = "MCP服务ID")
    private String mcpServerId;

    @ApiModelProperty(value = "MCP 工具名称")
    private String mcpToolName;

    @ApiModelProperty(value = "MCP 输入架构 JSON")
    private String mcpInputSchema;

    @ApiModelProperty(value = "超时时间（毫秒）")
    private Integer timeoutMs;

    @ApiModelProperty(value = "状态：0-禁用，1-启用")
    private Integer status;

    @ApiModelProperty(value = "备注")
    private String remark;
}
