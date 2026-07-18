package com.aether.sys.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_admin_preference")
public class AdminPreference extends BaseEntity {

    private String adminId;

    private String category;

    private String keyName;

    private String value;

    private String description;

    private Integer priority;

    private String scope;

    private String scopeDetail;

    private String source;

    private BigDecimal confidence;

    private Integer usageCount;

    private Long lastUsedAt;

    private Long expiresAt;

    private BigDecimal decayRate;

    private BigDecimal effectiveScore;

    private Integer status;

    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_ENABLED = 1;

    public static final String SOURCE_EXPLICIT = "explicit";
    public static final String SOURCE_IMPLICIT = "implicit";

    public static final String SCOPE_GLOBAL = "global";
    public static final String SCOPE_SESSION = "session";
    public static final String SCOPE_TASK_TYPE = "task_type";

    public static final String CATEGORY_LANGUAGE = "language";
    public static final String CATEGORY_STYLE = "style";
    public static final String CATEGORY_FORMAT = "format";
    public static final String CATEGORY_TECH_STACK = "tech_stack";
    public static final String CATEGORY_TOOL_STRATEGY = "tool_strategy";
}
