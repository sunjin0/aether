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
}
