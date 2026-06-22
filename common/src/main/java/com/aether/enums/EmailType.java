package com.aether.enums;


import lombok.Getter;

@Getter
public enum EmailType {
    //    通知，验证码
    NOTICE("Message_Type_Notification"),
    VERIFICATION_CODE("Message_Type_Verification");

    private final String code;

    EmailType(String value) {
        this.code = value;
    }
}
