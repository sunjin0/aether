package com.aether.agent.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 工具调用日志
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("agent_tool_call_log")
@ApiModel(value = "AgentToolCallLog对象", description = "工具调用日志")
public class AgentToolCallLog extends BaseEntity {

    @ApiModelProperty(value = "租户边界")
    private String tenantId;

    /** 统一 Execution 账本中的节点 ID。 */
    private String executionId;

    @ApiModelProperty(value = "所属业务应用空间")
    private String applicationId;

    @ApiModelProperty(value = "关联运行记录ID")
    private String runId;

    @ApiModelProperty(value = "关联工具ID")
    private String toolId;

    @ApiModelProperty(value = "模型返回的tool call id（如call_xxx）")
    private String toolCallId;

    @ApiModelProperty(value = "工具名称")
    private String toolName;

    @ApiModelProperty(value = "模型传给工具的原始参数JSON")
    private String arguments;

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

    @ApiModelProperty(value = "响应体（截断存储，最大64KB）")
    private String responseBody;

    @ApiModelProperty(value = "执行耗时（毫秒）")
    private Integer latencyMs;

    @ApiModelProperty(value = "状态：0-成功，1-失败，2-超时，3-安全拦截")
    private Integer status;

    @ApiModelProperty(value = "错误信息")
    private String errorMsg;
}
