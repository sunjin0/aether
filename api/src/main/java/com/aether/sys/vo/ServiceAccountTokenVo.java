package com.aether.sys.vo;

import lombok.Data;

@Data
public class ServiceAccountTokenVo {
    private String accessToken;
    private String tokenType = "Bearer";
    private Integer expiresIn;
}
