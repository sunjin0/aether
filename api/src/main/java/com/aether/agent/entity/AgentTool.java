package com.aether.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 工具
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("agent_tool")
@ApiModel(value = "AgentTool对象", description = "工具")
public class AgentTool extends BaseEntity {

    @ApiModelProperty(value = "工具名称")
    private String name;

    @ApiModelProperty(value = "工具编码（唯一）")
    private String code;

    @ApiModelProperty(value = "描述")
    private String description;

    @ApiModelProperty(value = "Tool business type, such as knowledge, ops, dev")
    private String toolType;

    @ApiModelProperty(value = "Tool type: mcp")
    @TableField(exist = false)
    private String type;

    /**
     * 内建工具的函数参数 Schema，不持久化到 agent_tool 表。
     */
    @TableField(exist = false)
    private String parametersSchema;

    @ApiModelProperty(value = "MCP服务ID")
    private String mcpServerId;

    @ApiModelProperty(value = "MCP tool name")
    private String mcpToolName;

    @ApiModelProperty(value = "MCP input schema JSON")
    private String mcpInputSchema;

    @ApiModelProperty(value = "超时时间（毫秒），默认30000")
    private Integer timeoutMs;

    @ApiModelProperty(value = "状态：0-禁用，1-启用")
    private Integer status;

    @ApiModelProperty(value = "备注")
    private String remark;
}
