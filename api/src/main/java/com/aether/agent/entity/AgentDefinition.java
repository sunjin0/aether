package com.aether.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * Agent定义
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("agent_definition")
@ApiModel(value = "AgentDefinition对象", description = "Agent定义")
public class AgentDefinition extends BaseEntity {

    @ApiModelProperty(value = "Agent名称")
    private String name;

    @ApiModelProperty(value = "Agent编码（唯一）")
    private String code;

    @ApiModelProperty(value = "描述")
    private String description;

    @ApiModelProperty(value = "系统提示词")
    private String systemPrompt;

    @ApiModelProperty(value = "关联模型供应商ID")
    private String modelProviderId;

    @ApiModelProperty(value = "使用的模型名称")
    private String model;

    @ApiModelProperty(value = "温度参数")
    private BigDecimal temperature;

    @ApiModelProperty(value = "最大token数")
    private Integer maxTokens;

    @ApiModelProperty(value = "状态：0-草稿，1-启用，2-禁用")
    private Integer status;

    @ApiModelProperty(value = "最大工具调用轮次，默认1")
    private Integer maxToolRounds;

    @ApiModelProperty(value = "默认是否启用深度思考")
    private Boolean defaultThinking;

    @ApiModelProperty(value = "默认推理力度：low/medium/high")
    private String defaultReasoningEffort;

    @ApiModelProperty(value = "访问类型：private/public，默认private")
    private String accessType;

    @ApiModelProperty(value = "排序号")
    private Integer sort;

    @ApiModelProperty(value = "备注")
    private String remark;
}
