package com.aether.sys.vo;

import com.aether.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 表示管理员偏好事件VO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminPreferenceEventVo extends BaseEntity {

    private String adminId;

    private String preferenceId;

    private String eventType;

    private String category;

    private String keyName;

    private String value;

    private BigDecimal confidence;

    private String conversationId;

    private String messageId;

    private String contextSnapshot;

    private Long current;

    private Long pageSize;
}
