package com.aether.workbench.vo;

import lombok.Data;

/**
 * A dashboard item with enough context for the client to route to its detail page.
 */
@Data
public class WorkbenchItemVo {
    private String type;
    private String id;
    private String title;
    private String status;
    private String description;
    private String workflowId;
    private Long createdAt;
    private Long deadlineAt;
    private boolean overdue;
    private Integer completedNodeCount;
    private Integer totalNodeCount;
}
