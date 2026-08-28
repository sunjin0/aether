package com.aether.openapi.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import java.util.Map;

/** 外部业务系统启动已发布工作流的稳定请求契约。 */
@Data
@ApiModel("OpenAPI 已发布工作流启动请求")
public class OpenApiWorkflowStartDto {
    @ApiModelProperty(value = "已发布工作流编码", required = true, example = "order-fulfillment")
    private String workflowCode;
    @ApiModelProperty(value = "已发布产品编码", required = true, example = "merchant-automation")
    private String productCode;
    @ApiModelProperty(value = "调用方业务记录 ID", required = true, example = "ORD-2048")
    private String businessId;
    @ApiModelProperty(value = "业务事件类型", required = true, example = "order.created")
    private String businessType;
    @ApiModelProperty(value = "用于安全重试提交的稳定唯一键", required = true, example = "order-created-ORD-2048")
    private String idempotencyKey;
    @ApiModelProperty(value = "完成事件的 HTTPS 回调 URL", example = "https://merchant.example.com/hooks/workflows")
    private String callbackUrl;
    @ApiModelProperty(value = "人工处理的可选截止时间，Unix 时间戳毫秒", example = "1767225600000")
    private Long deadlineAt;
    @ApiModelProperty(value = "供工作流使用的输入变量", example = "{\"orderId\":\"ORD-2048\",\"amount\":125.50}")
    private Map<String, Object> input;
}
