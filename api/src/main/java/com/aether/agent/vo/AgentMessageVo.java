package com.aether.agent.vo;

import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 消息 VO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentMessageVo extends BaseEntity {

    @ApiModelProperty(value = "关联会话ID")
    private String conversationId;

    @ApiModelProperty(value = "角色：user、assistant、tool")
    private String role;

    @ApiModelProperty(value = "消息内容")
    private String content;

    @ApiModelProperty(value = "推理内容")
    private String reasoningContent;

    @ApiModelProperty(value = "工具调用请求（JSON格式）")
    private String toolCalls;

    @ApiModelProperty(value = "工具调用ID")
    private String toolCallId;

    @ApiModelProperty(value = "工具调用结果")
    private String toolResult;

    @ApiModelProperty(value = "使用的模型")
    private String model;

    @ApiModelProperty(value = "输入token数")
    private Integer promptTokens;

    @ApiModelProperty(value = "输出token数")
    private Integer completionTokens;

    @ApiModelProperty(value = "总token数")
    private Integer totalTokens;

    @ApiModelProperty(value = "推理token数")
    private Integer reasoningTokens;

    @ApiModelProperty(value = "响应延迟（毫秒）")
    private Integer latencyMs;

    @ApiModelProperty(value = "是否编辑：0-未编辑，1-已编辑")
    private Integer edited;

    @ApiModelProperty(value = "编辑前的原始内容")
    private String originalContent;

    @ApiModelProperty(value = "编辑时间")
    private Long editedAt;

    @ApiModelProperty(value = "关联运行记录ID")
    private String runId;

    @ApiModelProperty(value = "工具调用日志列表")
    private List<AgentToolCallLogVo> toolCallLogs;

    private Long current;
    private Long pageSize;
}
