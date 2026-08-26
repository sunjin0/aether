package com.aether.openapi.vo;

import lombok.Data;

/** 对外安全运行视图，不泄露内部节点、工具及异常细节。 */
@Data
public class OpenApiRunVo {
    private String runId;
    private String businessId;
    private String status;
    private String traceId;
    private String errorCode;
}
