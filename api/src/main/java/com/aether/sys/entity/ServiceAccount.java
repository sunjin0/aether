package com.aether.sys.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 业务系统使用的非交互式服务账号。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_service_account")
public class ServiceAccount extends BaseEntity {
    private String userId;
    private String name;
    private String description;
    private String clientId;
    /** BCrypt 哈希，绝不向 API 返回。 */
    private String secretHash;
    /** 轮换或禁用时递增，已签发令牌会立即失效。 */
    private Integer tokenVersion;
    private Boolean enabled;
    /** JSON 数组；为空数组表示不限制可启动的工作流。 */
    private String allowedWorkflowIds;
    /** 每小时最大业务启动次数；0 表示不限制。 */
    private Integer maxStartsPerHour;
    private Long lastUsedAt;
}
