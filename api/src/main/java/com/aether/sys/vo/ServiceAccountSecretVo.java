package com.aether.sys.vo;

import lombok.Data;

/** 创建或轮换时一次性返回的明文密钥。 */
@Data
public class ServiceAccountSecretVo {
    private String id;
    private String name;
    private String clientId;
    private String clientSecret;
}
