package com.aether.agent.product.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 可对外发布的业务能力。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_product_profile")
public class AgentProductProfile extends BaseEntity {
    /** Stable logical product identifier shared by all of its versions. */
    private String productId;
    private String applicationId;
    private String code;
    private String agentDefinitionId;
    private String workflowId;
    /** AGENT / WORKFLOW */
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
    private String apiProtocolVersion;
    /** JSON declaration of externally accepted trusted-context keys. */
    private String allowedContextKeys;
    /** Immutable profile-version snapshot selected at publish time. */
    private String publishedSnapshotId;
}
