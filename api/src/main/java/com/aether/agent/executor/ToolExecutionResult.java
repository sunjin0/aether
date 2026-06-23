package com.aether.agent.executor;

import lombok.Data;

/**
 * 工具执行结果。
 */
@Data
public class ToolExecutionResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 提取后的结果内容
     */
    private String content;

    /**
     * 原始响应体
     */
    private String rawResponse;

    /**
     * HTTP状态码
     */
    private Integer httpStatus;

    /**
     * 执行耗时（毫秒）
     */
    private Integer latencyMs;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 状态：0-成功，1-失败，2-超时，3-安全拦截
     */
    private Integer status;

    public static ToolExecutionResult success(String content, String rawResponse, Integer httpStatus, Integer latencyMs) {
        ToolExecutionResult result = new ToolExecutionResult();
        result.setSuccess(true);
        result.setContent(content);
        result.setRawResponse(rawResponse);
        result.setHttpStatus(httpStatus);
        result.setLatencyMs(latencyMs);
        result.setStatus(0);
        return result;
    }

    public static ToolExecutionResult failure(String errorMsg, Integer status) {
        ToolExecutionResult result = new ToolExecutionResult();
        result.setSuccess(false);
        result.setErrorMsg(errorMsg);
        result.setStatus(status);
        return result;
    }
}
