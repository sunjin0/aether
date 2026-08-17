package com.aether.agent.vo;

import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表示智能体知识库BaseBindingVO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentKnowledgeBaseBindingVo extends BaseEntity {

    @ApiModelProperty(value = "Agent definition ID")
    private String agentDefinitionId;

    @ApiModelProperty(value = "Knowledge base ID")
    private String knowledgeBaseId;

    @ApiModelProperty(value = "Knowledge base name")
    private String knowledgeBaseName;

    @ApiModelProperty(value = "Knowledge base scope: PLATFORM or AGENT")
    private String scope;

    @ApiModelProperty(value = "status: 0-disabled, 1-enabled")
    private Integer status;

    private Long current;

    private Long pageSize;
}
