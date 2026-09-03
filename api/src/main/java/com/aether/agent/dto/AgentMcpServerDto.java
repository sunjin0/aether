package com.aether.agent.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * MCP服务 DTO
 */
@Data
@ApiModel("MCP 服务器创建或更新请求")
public class AgentMcpServerDto {

    @ApiModelProperty(value = "MCP服务名称")
    private String name;

    @ApiModelProperty(value = "MCP服务编码")
    private String code;

    @ApiModelProperty(value = "Connector 配置版本")
    private String version;

    @ApiModelProperty(value = "传输类型：http/streamable_http")
    private String transport;

    @ApiModelProperty(value = "MCP 端点")
    private String baseUrl;

    @ApiModelProperty(value = "请求头JSON")
    private String requestHeaders;

    @ApiModelProperty(value = "认证类型：none/bearer/api_key")
    private String authType;

    @ApiModelProperty(value = "认证token")
    private String authToken;

    @ApiModelProperty(value = "是否清空认证token")
    private Boolean clearAuthToken;

    @ApiModelProperty(value = "超时时间（毫秒）")
    private Integer timeoutMs;

    @ApiModelProperty(value = "状态：0-禁用，1-启用")
    private Integer status;

    @ApiModelProperty(value = "备注")
    private String remark;
}
