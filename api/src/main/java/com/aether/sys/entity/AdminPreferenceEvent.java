package com.aether.sys.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_admin_preference_event")
public class AdminPreferenceEvent extends BaseEntity {

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

    public static final String EVENT_EXTRACT = "extract";
    public static final String EVENT_CONFIRM = "confirm";
    public static final String EVENT_REJECT = "reject";
    public static final String EVENT_OVERRIDE = "override";
    public static final String EVENT_USE = "use";
}
