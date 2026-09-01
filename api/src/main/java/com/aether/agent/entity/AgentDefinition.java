package com.aether.agent.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
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

    @ApiModelProperty(value = "所属租户")
    private String tenantId;

    @ApiModelProperty(value = "所属业务应用空间")
    private String applicationId;

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

    /**
     * Preferred catalog model. provider/model remain for legacy compatibility.
     */
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

    @ApiModelProperty(value = "Execution mode: STANDARD or DEEP")
    private String executionMode;

    @ApiModelProperty(value = "Agent 对外发信邮箱")
    private String smtpSenderEmail;

    @ApiModelProperty(value = "是否启用 Agent 邮件发送")
    private Boolean smtpEnabled;

    @ApiModelProperty(value = "Agent 发信 SMTP 主机")
    private String smtpHost;

    @ApiModelProperty(value = "Agent 发信 SMTP 端口")
    private Integer smtpPort;

    @ApiModelProperty(value = "Agent 发信 SMTP 加密方式：ssl/starttls")
    private String smtpSecurity;

    /** 已 AES 加密；任何接口均不得回传明文或密文。 */
    @ApiModelProperty(value = "Agent SMTP 授权码（仅写入）")
    private String smtpAuthorizationCode;
}
