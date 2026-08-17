package com.aether.sys.vo;

import com.aether.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 表示管理员偏好VO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminPreferenceVo extends BaseEntity {

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

    private Long current;

    private Long pageSize;
}
