package com.aether.agent.vo;

import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * Agent定义 VO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentDefinitionVo extends BaseEntity {

    @ApiModelProperty(value = "所属业务应用空间")
    private String applicationId;

    @ApiModelProperty(value = "Agent名称")
    private String name;

    @ApiModelProperty(value = "Agent编码")
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

    @ApiModelProperty(value = "关联模型供应商名称")
    private String modelProviderName;

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

    @ApiModelProperty(value = "Agent 对外发信邮箱")
    private String smtpSenderEmail;
    private Boolean smtpEnabled;
    private String smtpHost;
    private Integer smtpPort;
    private String smtpSecurity;

    /** 仅表示是否已保存授权码，绝不返回授权码本身。 */
    private Boolean smtpAuthorizationCodeConfigured;

    @ApiModelProperty(value = "绑定的工具列表")
    private List<String> toolIds;

    private Long current;
    private Long pageSize;
}
