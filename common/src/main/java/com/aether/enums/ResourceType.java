package com.aether.enums;

import lombok.Getter;

@Getter
public enum ResourceType {
    /**
     * 路由
     */
    ROUTE("Resource_Type_Route"),
    /**
     * 权限
     */
    PERMISSION("Resource_Type_Permission");
    private final String code;

    ResourceType(String code) {
        this.code = code;
    }

}
