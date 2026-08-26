package com.aether.agent.application.vo;

import com.aether.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 业务应用空间安全视图。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentApplicationVo extends BaseEntity {
    private String code;
    private String name;
    private String description;
    private Integer status;
    private Long current;
    private Long pageSize;
}
