package com.aether.agent.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表示智能体工具路由索引。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_tool_routing_index")
public class AgentToolRoutingIndex extends BaseEntity {
    private String toolId;
    private String contentHash;
    private String embeddingProviderId;
    private String embeddingModel;
    private String embedding;
    /**
     * 0-pending, 1-ready, 2-failed
     */
    private Integer indexStatus;
    private String failureReason;
    private Long indexedAt;
    /**
     * Similarity returned only by the routing query; it is not persisted.
     */
    @TableField(exist = false)
    private Double vectorScore;
}
