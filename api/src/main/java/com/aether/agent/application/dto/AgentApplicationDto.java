package com.aether.agent.application.dto;

import lombok.Data;

/** 业务应用空间写入参数。 */
@Data
public class AgentApplicationDto {
    private String code;
    private String name;
    private String description;
    private Integer status;
}
