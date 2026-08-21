package com.aether.agent.vo;

import lombok.Data;

/**
 * 服务账号可调用 Agent。
 */
@Data
public class BusinessAgentOptionVo {
    private String id;
    private String name;
    private String code;
    private String description;
    private String executionMode;
}
