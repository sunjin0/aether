package com.aether.agent.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 工具绑定
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("agent_tool_binding")
@ApiModel(value = "AgentToolBinding对象", description = "工具绑定")
public class AgentToolBinding extends BaseEntity {

    @ApiModelProperty(value = "关联Agent定义ID")
    private String agentDefinitionId;

    @ApiModelProperty(value = "关联工具ID")
    private String toolId;

    @ApiModelProperty(value = "调用优先级，默认0")
    private Integer priority;

    @ApiModelProperty(value = "状态：0-禁用，1-启用")
    private Integer status;
}
