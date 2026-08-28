package com.aether.agent.vo;

import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会话 VO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentConversationVo extends BaseEntity {

    @ApiModelProperty(value = "用户ID")
    private String userId;

    @ApiModelProperty(value = "是否由外部服务账号发起")
    private Boolean external;

    @ApiModelProperty(value = "关联Agent定义ID")
    private String agentDefinitionId;

    @ApiModelProperty(value = "Agent名称")
    private String agentDefinitionName;

    @ApiModelProperty(value = "关联Agent执行模式：STANDARD或DEEP")
    private String executionMode;

    @ApiModelProperty(value = "会话标题")
    private String title;

    @ApiModelProperty(value = "消息数")
    private Integer messageCount;

    @ApiModelProperty(value = "状态：0-进行中，1-关闭，2-归档")
    private Integer status;

    @ApiModelProperty(value = "会话工具确认策略：ask、risky、never")
    private String toolApprovalPolicy;

    private Long current;
    private Long pageSize;
}
