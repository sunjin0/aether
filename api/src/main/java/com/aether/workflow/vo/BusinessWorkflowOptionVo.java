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
    /** 已发布版本的开始节点输入字段定义（JSON）。 */
    private String inputSchema;
    /** 已发布版本的最终输出字段定义（JSON）。 */
    private String outputSchema;
}
