package com.aether.sys.dto;

import lombok.Data;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** Service-account client credentials used only to obtain a Front access token. */
@Data
@ApiModel(description = "Front 服务账号换取访问令牌请求")
public class ServiceAccountTokenDto {
    @ApiModelProperty(value = "后台创建服务账号时生成或指定的客户端 ID；长度 3-64，只能包含字母、数字、下划线和连字符", required = true, example = "sa_order_service")
    private String clientId;

    @ApiModelProperty(value = "创建或轮换服务账号时仅展示一次的客户端密钥；不得放入浏览器、日志或 URL", required = true, example = "sa_********")
    private String clientSecret;
}
