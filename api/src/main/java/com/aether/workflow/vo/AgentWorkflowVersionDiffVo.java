package com.aether.workflow.vo;

import lombok.Data;
import java.util.List;

/** 两个发布版本的结构化差异，供管理端展示和审计。 */
@Data
public class AgentWorkflowVersionDiffVo {
    private Integer fromVersion;
    private Integer toVersion;
    private List<String> addedNodeIds;
    private List<String> removedNodeIds;
    private List<String> changedNodeIds;
    private List<String> addedEdgeIds;
    private List<String> removedEdgeIds;
    private boolean inputSchemaChanged;
    private boolean outputSchemaChanged;
}
