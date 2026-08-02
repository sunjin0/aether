package com.aether.agent.dto;

import lombok.Data;
import java.util.Map;

/**
 * 业务系统启动工作流的请求契约。
 * <p>调用方应使用服务账号令牌，并为每次业务事件生成稳定且唯一的 idempotencyKey。</p>
 */
@Data
public class AgentWorkflowBusinessStartDto {
    private Map<String, Object> variables;
    private String businessType;
    private String businessId;
    private String idempotencyKey;
    private String callbackUrl;
    /** 人工等待 SLA 截止时间（Unix 毫秒）；为空表示不设置截止。 */
    private Long deadlineAt;
}
