package com.aether.agent.product.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Agent 产品发布后的不可变配置快照。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_product_profile_version")
public class AgentProductProfileVersion extends BaseEntity {
    private String profileId;
    private Integer versionNo;
    private String snapshot;
    private String publishedBy;
    private Long publishedAt;
}
