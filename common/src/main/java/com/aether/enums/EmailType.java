package com.aether.enums;


import lombok.Getter;

/**
 * 表示EmailType。
 */
@Getter
public enum EmailType {
    //    通知，验证码
    NOTICE("Message_Type_Notification"),
    VERIFICATION_CODE("Message_Type_Verification");

    private final String code;

    /**
     * 创建 {@code EmailType} 实例。
     */
    EmailType(String value) {
        this.code = value;
    }
}
