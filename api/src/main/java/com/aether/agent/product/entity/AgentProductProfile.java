package com.aether.agent.product.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 可对外发布的 Agent 产品配置快照。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_product_profile")
public class AgentProductProfile extends BaseEntity {
    private String applicationId;
    private String agentDefinitionId;
    /** CUSTOMER_SERVICE / KNOWLEDGE_QA / BUSINESS_ASSISTANT */
    private String productType;
    private String name;
    private String inputSchema;
    private String outputSchema;
    private String knowledgePolicy;
    private String approvalPolicy;
    private String handoffPolicy;
    private Integer status;
    private Integer versionNo;
    private Long publishedAt;
}
