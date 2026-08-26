package com.aether.openapi.dto;

import lombok.Data;
import java.util.Map;

/** 外部业务系统启动已发布工作流的稳定请求契约。 */
@Data
public class OpenApiWorkflowStartDto {
    private String workflowCode;
    private String businessId;
    private String businessType;
    private String idempotencyKey;
    private String callbackUrl;
    private Long deadlineAt;
    private Map<String, Object> input;
}
