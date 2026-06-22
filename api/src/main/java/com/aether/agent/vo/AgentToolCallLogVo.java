package com.aether.agent.vo;

import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工具调用日志 VO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentToolCallLogVo extends BaseEntity {

    @ApiModelProperty(value = "关联运行记录ID")
    private String runId;

    @ApiModelProperty(value = "关联工具ID")
    private String toolId;

    @ApiModelProperty(value = "工具名称")
    private String toolName;

    @ApiModelProperty(value = "关联Agent定义ID")
    private String agentDefinitionId;

    @ApiModelProperty(value = "实际请求URL")
    private String requestUrl;

    @ApiModelProperty(value = "实际请求方法")
    private String requestMethod;

    @ApiModelProperty(value = "实际请求头（JSON）")
    private String requestHeaders;

    @ApiModelProperty(value = "实际请求体")
    private String requestBody;

    @ApiModelProperty(value = "HTTP响应状态码")
    private Integer responseStatus;

    @ApiModelProperty(value = "响应体")
    private String responseBody;

    @ApiModelProperty(value = "执行耗时（毫秒）")
    private Integer latencyMs;

    @ApiModelProperty(value = "状态：0-成功，1-失败，2-超时，3-安全拦截")
    private Integer status;

    @ApiModelProperty(value = "错误信息")
    private String errorMsg;

    private Long current;
    private Long pageSize;
}
