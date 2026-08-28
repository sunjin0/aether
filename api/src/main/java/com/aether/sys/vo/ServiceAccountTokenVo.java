package com.aether.sys.vo;

import lombok.Data;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** Front business API access token response. */
@Data
@ApiModel(description = "Front 服务账号访问令牌响应")
public class ServiceAccountTokenVo {
    @ApiModelProperty(value = "调用 Front 业务接口的短期访问令牌；只可通过 Authorization 请求头传递", example = "encrypted-access-token")
    private String accessToken;
    @ApiModelProperty(value = "Authorization 请求头认证方案，固定为 Bearer", example = "Bearer")
    private String tokenType = "Bearer";
    @ApiModelProperty(value = "令牌有效期，单位秒；到期后需重新使用 clientId/clientSecret 换取", example = "900")
    private Integer expiresIn;
}
