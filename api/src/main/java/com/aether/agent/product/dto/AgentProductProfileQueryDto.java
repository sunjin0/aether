package com.aether.agent.product.dto;

import lombok.Data;

@Data
public class AgentProductProfileQueryDto {
    private String applicationId;
    private String name;
    private String productType;
    private Integer status;
    private Long current;
    private Long pageSize;
}
