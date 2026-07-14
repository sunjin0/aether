package com.aether.agent.vo;

import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工具 VO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentToolVo extends BaseEntity {

    @ApiModelProperty(value = "工具名称")
    private String name;

    @ApiModelProperty(value = "工具编码")
    private String code;

    @ApiModelProperty(value = "描述")
    private String description;

    @ApiModelProperty(value = "Tool business type, such as knowledge, ops, dev")
    private String toolType;

    @ApiModelProperty(value = "Total call count")
    private Long callCount;

    @ApiModelProperty(value = "Success rate")
    private Double successRate;

    @ApiModelProperty(value = "MCP服务ID")
    private String mcpServerId;

    @ApiModelProperty(value = "MCP服务名称")
    private String mcpServerName;

    @ApiModelProperty(value = "MCP服务地址")
    private String mcpBaseUrl;

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

    private Long current;
    private Long pageSize;
}
