package com.aether.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 运行记录
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("agent_run")
@ApiModel(value = "AgentRun对象", description = "运行记录")
public class AgentRun extends BaseEntity {

    @ApiModelProperty(value = "关联Agent定义ID")
    private String agentDefinitionId;

    @ApiModelProperty(value = "用户ID")
    private String userId;

    @ApiModelProperty(value = "关联会话ID")
    private String conversationId;

    @ApiModelProperty(value = "关联输出消息ID")
    private String messageId;

    @ApiModelProperty(value = "输入内容摘要")
    private String inputContent;

    @ApiModelProperty(value = "输出内容摘要")
    private String outputContent;

    @ApiModelProperty(value = "使用的模型")
    private String model;

    @ApiModelProperty(value = "使用的模型供应商ID")
    private String modelProviderId;

    @ApiModelProperty(value = "输入token数")
    private Integer promptTokens;

    @ApiModelProperty(value = "输出token数")
    private Integer completionTokens;

    @ApiModelProperty(value = "总token数")
    private Integer totalTokens;

    @ApiModelProperty(value = "总耗时（毫秒）")
    private Integer latencyMs;

    @ApiModelProperty(value = "状态：0-成功，1-失败，2-超时")
    private Integer status;

    @ApiModelProperty(value = "错误信息")
    private String errorMsg;

    @ApiModelProperty(value = "External Deep Agent run ID")
    private String externalRunId;

    @ApiModelProperty(value = "Deep Agent dispatch-time retrieval sources JSON")
    private String retrievalSources;

    @ApiModelProperty(value = "Execution mode: STANDARD or DEEP")
    private String executionMode;

    /** 本次运行冻结的 Skill、工具和知识库作用域快照 JSON。 */
    @ApiModelProperty(value = "Resolved Skill context snapshot JSON")
    private String skillSnapshot;
}
