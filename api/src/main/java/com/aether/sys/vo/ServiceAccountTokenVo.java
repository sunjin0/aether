package com.aether.sys.vo;

import lombok.Data;

/**
 * 表示服务账户令牌VO。
 */
@Data
public class ServiceAccountTokenVo {
    private String accessToken;
    private String tokenType = "Bearer";
    private Integer expiresIn;
}
