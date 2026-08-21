package com.aether.workflow.vo;

import lombok.Data;

/**
 * 服务账号可启动工作流。
 */
@Data
public class BusinessWorkflowOptionVo {
    private String id;
    private String name;
    private String description;
    private Integer publishedVersion;
}
