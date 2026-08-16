package com.aether.agent.vo;

import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 运行记录 VO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentRunVo extends BaseEntity {

    @ApiModelProperty(value = "关联Agent定义ID")
    private String agentDefinitionId;

    @ApiModelProperty(value = "Agent名称")
    private String agentDefinitionName;

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

    @ApiModelProperty(value = "等待用户输入/审批的耗时（毫秒）；执行耗时 = latencyMs - waitingMs")
    private Long waitingMs;

    @ApiModelProperty(value = "状态：0-成功，1-失败，2-超时")
    private Integer status;

    @ApiModelProperty(value = "错误信息")
    private String errorMsg;

    private Long current;
    private Long pageSize;

    @ApiModelProperty(value = "创建时间起始时间戳")
    private Long startTime;

    @ApiModelProperty(value = "创建时间结束时间戳")
    private Long endTime;

    @ApiModelProperty(value = "执行模式：STANDARD 或 DEEP")
    private String executionMode;

    @ApiModelProperty(value = "外部 Deep Agent 运行 ID")
    private String externalRunId;

    @ApiModelProperty(value = "Deep Agent dispatch-time retrieval sources JSON")
    private String retrievalSources;
}
