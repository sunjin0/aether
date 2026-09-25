package com.aether.agent.entity;

import com.aether.entity.AccountOwnedEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 工具
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("agent_tool")
@ApiModel(value = "AgentTool对象", description = "工具")
public class AgentTool extends AccountOwnedEntity {

    @ApiModelProperty(value = "工具名称")
    private String name;

    @ApiModelProperty(value = "工具编码（唯一）")
    private String code;

    @ApiModelProperty(value = "描述")
    private String description;

    @ApiModelProperty(value = "系统图标库名称")
    private String icon;

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

    /** 工作流能力工具的后端绑定标识，不持久化到 agent_tool。 */
    @TableField(exist = false)
    private String workflowCapabilityId;

    /** 工作流能力绑定的已发布版本，不持久化到 agent_tool。 */
    @TableField(exist = false)
    private String workflowVersionId;

    /** 工作流能力工具动作：START、OBSERVE、STOP 等。 */
    @TableField(exist = false)
    private String workflowToolAction;

    @ApiModelProperty(value = "MCP服务ID")
    private String mcpServerId;

    @ApiModelProperty(value = "MCP tool name")
    private String mcpToolName;

    @ApiModelProperty(value = "MCP input schema JSON")
    private String mcpInputSchema;

    @ApiModelProperty(value = "是否常驻携带到模型工具列表；仅 MCP 工具配置生效")
    private Boolean resident;

    @ApiModelProperty(value = "超时时间（毫秒），默认30000")
    private Integer timeoutMs;

    @ApiModelProperty(value = "状态：0-禁用，1-启用")
    private Integer status;

    @ApiModelProperty(value = "备注")
    private String remark;
}
