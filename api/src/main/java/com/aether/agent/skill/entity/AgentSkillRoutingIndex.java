package com.aether.agent.skill.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_skill_routing_index")
public class AgentSkillRoutingIndex extends BaseEntity {
    private String skillVersionId;
    private String contentHash;
    private String embeddingProviderId;
    private String embeddingModel;
    private String embedding;
    /** 0-pending, 1-ready, 2-failed */
    private Integer indexStatus;
    private String failureReason;
    private Long indexedAt;
    /** Similarity returned only by the routing query; it is not persisted. */
    @TableField(exist = false)
    private Double vectorScore;
}
