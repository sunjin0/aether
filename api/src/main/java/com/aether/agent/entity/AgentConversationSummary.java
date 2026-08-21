package com.aether.agent.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * Structured, validated rolling summary for one Agent conversation.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("agent_conversation_summary")
@ApiModel(value = "AgentConversationSummary对象", description = "会话结构化摘要")
public class AgentConversationSummary extends BaseEntity {
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";

    @ApiModelProperty(value = "会话ID")
    private String conversationId;

    @ApiModelProperty(value = "结构化摘要JSON")
    private String contentJson;

    @ApiModelProperty(value = "连续覆盖到的消息ID")
    private String coveredUntilMessageId;

    @ApiModelProperty(value = "连续覆盖到的消息创建时间")
    private Long coveredUntilCreatedAt;

    @ApiModelProperty(value = "生成依据的记忆版本")
    private Integer sourceMemoryVersion;

    @ApiModelProperty(value = "生成依据的事件范围")
    private String sourceEventRange;

    @ApiModelProperty(value = "最高敏感级别")
    private String sourceSensitivityMax;

    @ApiModelProperty(value = "摘要版本")
    private Integer summaryVersion;

    @ApiModelProperty(value = "幂等刷新ID")
    private String refreshId;

    @ApiModelProperty(value = "压缩模型ID或模型名")
    private String modelId;

    @ApiModelProperty(value = "压缩输入token")
    private Integer inputTokens;

    @ApiModelProperty(value = "压缩输出token")
    private Integer outputTokens;

    @ApiModelProperty(value = "状态：READY/REFRESHING/FAILED")
    private String status;
}

