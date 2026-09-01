package com.aether.sys.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务系统使用的非交互式服务账号。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_service_account")
public class ServiceAccount extends BaseEntity {
    /** 服务账号所属租户。 */
    private String tenantId;
    /** 所属业务应用空间。 */
    private String applicationId;
    private String name;
    private String description;
    private String clientId;
    /**
     * BCrypt 哈希，绝不向 API 返回。
     */
    private String secretHash;
    /**
     * 轮换或禁用时递增，已签发令牌会立即失效。
     */
    private Integer tokenVersion;
    private Boolean enabled;
    /** JSON 数组；允许调用的已发布产品。 */
    private String allowedProductIds;
    /**
     * 每小时最大业务启动次数；0 表示不限制。
     */
    private Integer maxStartsPerHour;
    /**
     * 每小时最大 Agent 调用次数；0 表示不限制。
     */
    private Integer maxAgentCallsPerHour;
    private Long lastUsedAt;
}
