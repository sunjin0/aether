package com.aether.agent.vo;

import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP服务 VO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentMcpServerVo extends BaseEntity {

    @ApiModelProperty(value = "MCP服务名称")
    private String name;

    @ApiModelProperty(value = "MCP服务编码")
    private String code;

    @ApiModelProperty(value = "Connector 配置版本")
    private String version;

    @ApiModelProperty(value = "传输类型：http/streamable_http")
    private String transport;

    @ApiModelProperty(value = "MCP endpoint")
    private String baseUrl;

    @ApiModelProperty(value = "请求头JSON")
    private String requestHeaders;

    @ApiModelProperty(value = "认证类型：none/bearer/api_key")
    private String authType;

    @ApiModelProperty(value = "认证token")
    private String authToken;

    private String credentialRef;

    @ApiModelProperty(value = "超时时间（毫秒）")
    private Integer timeoutMs;

    @ApiModelProperty(value = "状态：0-禁用，1-启用")
    private Integer status;

    private String healthStatus;
    private Long healthCheckedAt;
    private String healthMessage;

    @ApiModelProperty(value = "备注")
    private String remark;

    private Long current;
    private Long pageSize;
}
