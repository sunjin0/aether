package com.aether.agent.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 表示智能体知识库BaseBinding。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("agent_knowledge_base_binding")
@ApiModel(value = "AgentKnowledgeBaseBinding", description = "Agent knowledge base binding")
public class AgentKnowledgeBaseBinding extends BaseEntity {

    @ApiModelProperty(value = "Agent definition ID")
    private String agentDefinitionId;

    @ApiModelProperty(value = "Knowledge base ID")
    private String knowledgeBaseId;

    @ApiModelProperty(value = "status: 0-disabled, 1-enabled")
    private Integer status;
}
