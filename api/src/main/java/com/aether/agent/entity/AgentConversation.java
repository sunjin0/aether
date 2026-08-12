package com.aether.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 会话
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("agent_conversation")
@ApiModel(value = "AgentConversation对象", description = "会话")
public class AgentConversation extends BaseEntity {

    @ApiModelProperty(value = "用户ID")
    private String userId;

    @ApiModelProperty(value = "关联Agent定义ID")
    private String agentDefinitionId;

    @ApiModelProperty(value = "会话标题")
    private String title;

    @ApiModelProperty(value = "消息数，默认0")
    private Integer messageCount;

    @ApiModelProperty(value = "状态：0-进行中，1-关闭，2-归档")
    private Integer status;

    @ApiModelProperty(value = "会话工具确认策略：ask-每次请求，risky-仅高风险请求，never-自动批准")
    private String toolApprovalPolicy;

    @ApiModelProperty(value = "持久化会话摘要")
    private String summary;

    @ApiModelProperty(value = "摘要覆盖到的消息ID")
    private String summaryCoveredMessageId;

    @ApiModelProperty(value = "摘要覆盖到的消息创建时间")
    private Long summaryCoveredCreatedAt;

    @ApiModelProperty(value = "摘要更新时间")
    private Long summaryUpdatedAt;
}
