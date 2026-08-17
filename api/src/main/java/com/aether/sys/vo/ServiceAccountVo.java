package com.aether.sys.vo;

import lombok.Data;

import java.util.List;

/**
 * 服务账号安全视图，不含 secretHash。
 */
@Data
public class ServiceAccountVo {
    private String id;
    private String userId;
    private String name;
    private String description;
    private String clientId;
    private Boolean enabled;
    private List<String> allowedWorkflowIds;
    private Integer maxStartsPerHour;
    private Long lastUsedAt;
    private Long createdAt;
    private List<String> roleIds;
    private Long current;
    private Long pageSize;
}
