package com.aether.sys.dto;

import lombok.Data;

/**
 * 表示服务账户令牌DTO。
 */
@Data
public class ServiceAccountTokenDto {
    private String clientId;
    private String clientSecret;
}
