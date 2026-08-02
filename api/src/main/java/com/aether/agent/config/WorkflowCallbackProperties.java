package com.aether.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 业务工作流回调的网络边界与投递策略。 */
@Data
@Component
@ConfigurationProperties(prefix = "aether.workflow.callback")
public class WorkflowCallbackProperties {
    /** 未显式启用时不发送外部 HTTP 回调。 */
    private boolean enabled = false;
    /** 允许接收回调的精确主机名列表。空列表表示拒绝所有。 */
    private List<String> allowedHosts = new ArrayList<String>();
    /** 回调 HMAC 签名密钥；启用回调时必须由环境变量提供。 */
    private String signingSecret;
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 10000;
    private int maxAttempts = 8;
}
