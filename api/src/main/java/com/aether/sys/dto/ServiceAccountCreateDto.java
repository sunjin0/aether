package com.aether.sys.dto;

import lombok.Data;

import java.util.List;

/**
 * 表示服务账户创建DTO。
 */
@Data
public class ServiceAccountCreateDto {
    private String applicationId;
    private String name;
    private String description;
    /**
     * 可选；不填写时由服务端生成。
     */
    private String clientId;
    private List<String> allowedProductIds;
    private Integer maxStartsPerHour;
    private Integer maxAgentCallsPerHour;
}
