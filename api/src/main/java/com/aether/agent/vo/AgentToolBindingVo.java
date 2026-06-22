package com.aether.agent.vo;

import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工具绑定 VO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentToolBindingVo extends BaseEntity {

    @ApiModelProperty(value = "关联Agent定义ID")
    private String agentDefinitionId;

    @ApiModelProperty(value = "关联工具ID")
    private String toolId;

    @ApiModelProperty(value = "工具名称")
    private String toolName;

    @ApiModelProperty(value = "工具编码")
    private String toolCode;

    @ApiModelProperty(value = "调用优先级")
    private Integer priority;

    @ApiModelProperty(value = "状态：0-禁用，1-启用")
    private Integer status;
}
