package com.aether.enums;

import lombok.Getter;

/**
 * 表示资源Type。
 */
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

    /**
     * 创建 {@code ResourceType} 实例。
     */
    ResourceType(String code) {
        this.code = code;
    }

}
