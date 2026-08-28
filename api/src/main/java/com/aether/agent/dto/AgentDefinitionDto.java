package com.aether.agent.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Agent定义 DTO
 */
@Data
@ApiModel("智能体定义创建或更新请求")
public class AgentDefinitionDto {

    @ApiModelProperty(value = "所属业务应用空间")
    private String applicationId;

    @ApiModelProperty(value = "智能体名称")
    private String name;

    @ApiModelProperty(value = "智能体编码")
    private String code;

    @ApiModelProperty(value = "描述")
    private String description;

    @ApiModelProperty(value = "系统提示词")
    private String systemPrompt;

    @ApiModelProperty(value = "关联模型供应商ID")
    private String modelProviderId;
    private String modelId;

    @ApiModelProperty(value = "上下文压缩模型目录ID；为空时跟随聊天模型")
    private String contextCompressionModelId;

    @ApiModelProperty(value = "使用的模型名称")
    private String model;

    @ApiModelProperty(value = "温度参数")
    private BigDecimal temperature;

    @ApiModelProperty(value = "最大token数")
    private Integer maxTokens;

    @ApiModelProperty(value = "状态：0-草稿，1-启用，2-禁用")
    private Integer status;

    @ApiModelProperty(value = "最大工具调用轮次")
    private Integer maxToolRounds;

    @ApiModelProperty(value = "访问类型")
    private String accessType;

    @ApiModelProperty(value = "排序号")
    private Integer sort;

    @ApiModelProperty(value = "备注")
    private String remark;

    @ApiModelProperty(value = "执行模式：STANDARD 或 DEEP")
    private String executionMode;

    @ApiModelProperty(value = "智能体对外发信邮箱")
    private String smtpSenderEmail;
    private Boolean smtpEnabled;
    private String smtpHost;
    private Integer smtpPort;
    private String smtpSecurity;

    /** 仅写入；服务端加密后保存，编辑时留空表示保留已有授权码。 */
    @ApiModelProperty(value = "智能体 SMTP 授权码（仅写入）")
    private String smtpAuthorizationCode;

    @ApiModelProperty(value = "绑定的工具ID列表")
    private List<String> toolIds;
}
