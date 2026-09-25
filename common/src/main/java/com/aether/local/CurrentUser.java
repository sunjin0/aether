package com.aether.local;

import java.util.HashMap;

/**
 * 当前用户信息
 *
 * @author sun
 * @since 2024/10/08
 */
public class CurrentUser {

    /**
     * 用户
     */
    private static final ThreadLocal<HashMap<String, String>> USER = new ThreadLocal<>();

    /**
     * 获取用户信息
     *
     * @return {@link String }
     */
    public static HashMap<String, String> getUser() {
        return USER.get();
    }

    /**
     * 设置
     *
     * @param map 地图
     */
    public static void set(HashMap<String, String> map) {
        USER.set(map);
    }

    /**
     * 删除
     */
    public static void remove() {
        USER.remove();
    }

    public static String organizationId() {
        return USER.get() == null ? null : USER.get().get("organizationId");
    }

    public static String teamId() {
        return USER.get() == null ? null : USER.get().get("teamId");
    }

    /** 当前登录账号 ID；业务数据归属统一使用此值。 */
    public static String userId() {
        return USER.get() == null ? null : USER.get().get("userId");
    }

    /** 管理员可跨账号查看数据，普通用户只能查看自己的数据。 */
    public static boolean administrator() {
        if (USER.get() == null) return false;
        String role = USER.get().get("role");
        if (role == null) return false;
        String normalized = role.trim().toUpperCase();
        return "ADMIN".equals(normalized);
    }

    /** 当前查询应使用的归属账号；管理员返回 null 表示不追加账号过滤。 */
    public static String dataOwnerId() {
        return administrator() ? null : userId();
    }
}
