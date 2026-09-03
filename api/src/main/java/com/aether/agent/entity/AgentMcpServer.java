package com.aether.agent.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * MCP server configuration.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("agent_mcp_server")
@ApiModel(value = "AgentMcpServer", description = "MCP server configuration")
public class AgentMcpServer extends BaseEntity {

    @ApiModelProperty(value = "Tenant owner")
    private String tenantId;

    @ApiModelProperty(value = "MCP server name")
    private String name;

    @ApiModelProperty(value = "MCP server code")
    private String code;

    /** Connector 配置版本，用于变更追踪与兼容性判断。 */
    private String version;

    @ApiModelProperty(value = "Transport type: http, sse, streamable_http")
    private String transport;

    @ApiModelProperty(value = "HTTP MCP endpoint")
    private String baseUrl;

    @ApiModelProperty(value = "Request headers JSON")
    private String requestHeaders;

    @ApiModelProperty(value = "Auth type: none, bearer, api_key")
    private String authType;

    @ApiModelProperty(value = "Encrypted auth token")
    private String authToken;

    @ApiModelProperty(value = "STDIO command, reserved")
    private String command;

    @ApiModelProperty(value = "STDIO args JSON, reserved")
    private String args;

    @ApiModelProperty(value = "Timeout in milliseconds")
    private Integer timeoutMs;

    @ApiModelProperty(value = "Status: 0-disabled, 1-enabled")
    private Integer status;

    /** Last connector health result: UNKNOWN, HEALTHY or UNHEALTHY. */
    private String healthStatus;
    private Long healthCheckedAt;
    private String healthMessage;

    @ApiModelProperty(value = "Remark")
    private String remark;
}
