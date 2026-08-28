package com.aether.workflow.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

/**
 * 业务系统启动工作流的请求契约。
 * <p>调用方应使用服务账号令牌，并为每次业务事件生成稳定且唯一的 idempotencyKey。</p>
 */
@Data
@ApiModel("服务账号工作流启动请求")
public class AgentWorkflowBusinessStartDto {
    @ApiModelProperty(value = "工作流输入变量", example = "{\"orderId\":\"ORD-2048\"}")
    private Map<String, Object> variables;
    @ApiModelProperty(value = "业务事件类型", required = true, example = "order.created")
    private String businessType;
    @ApiModelProperty(value = "业务记录 ID", required = true, example = "ORD-2048")
    private String businessId;
    @ApiModelProperty(value = "用于安全重试提交的稳定唯一键", required = true, example = "order-created-ORD-2048")
    private String idempotencyKey;
    @ApiModelProperty(value = "完成事件的 HTTPS 回调 URL", example = "https://merchant.example.com/hooks/workflows")
    private String callbackUrl;
    /**
     * 人工等待 SLA 截止时间（Unix 毫秒）；为空表示不设置截止。
     */
    @ApiModelProperty(value = "人工处理的可选截止时间，Unix 时间戳毫秒", example = "1767225600000")
    private Long deadlineAt;
}
