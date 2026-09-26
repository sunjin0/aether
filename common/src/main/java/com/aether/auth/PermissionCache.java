package com.aether.auth;

import com.aether.utils.TokenUtils;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/** Redis 中用户权限快照的统一读写入口。 */
public final class PermissionCache {
    /** 兼容现有 TokenList 数据，权限快照统一存放在该 Redis hash。 */
    public static final String PERMISSION_HASH = TokenUtils.TOKEN_KEY;
    /** 用户角色类型单独存储，避免把元数据暴露给前端 permissionMap。 */
    public static final String ROLE_TYPE_HASH = "RoleTypeList";

    private PermissionCache() {
    }

    /**
     * 从 Redis 读取权限快照，兼容 HashMap、LinkedHashMap 等 Map 实现。
     */
    public static Map<String, Object> get(RedisTemplate<String, Object> redisTemplate, String userId) {
        if (redisTemplate == null || userId == null || userId.trim().isEmpty()) return null;
        Object value = redisTemplate.opsForHash().get(PERMISSION_HASH, userId);
        return asMap(value);
    }

    /** 写入权限快照。快照不设置 TTL，生命周期由角色/资源变更和用户退出显式失效。 */
    public static void put(RedisTemplate<String, Object> redisTemplate, String userId,
                           Map<String, Object> permissionMap) {
        if (redisTemplate == null || userId == null || userId.trim().isEmpty() || permissionMap == null) return;
        redisTemplate.opsForHash().put(PERMISSION_HASH, userId, new LinkedHashMap<>(permissionMap));
    }

    /** 写入角色类型快照。 */
    public static void putRoleType(RedisTemplate<String, Object> redisTemplate, String userId, String roleType) {
        if (redisTemplate == null || userId == null || userId.trim().isEmpty() || roleType == null) return;
        redisTemplate.opsForHash().put(ROLE_TYPE_HASH, userId, roleType);
    }

    /** 读取角色类型快照。 */
    public static String getRoleType(RedisTemplate<String, Object> redisTemplate, String userId) {
        if (redisTemplate == null || userId == null || userId.trim().isEmpty()) return null;
        Object value = redisTemplate.opsForHash().get(ROLE_TYPE_HASH, userId);
        return value == null ? null : String.valueOf(value);
    }

    /** 删除用户的权限和角色快照。 */
    public static void evict(RedisTemplate<String, Object> redisTemplate, String userId) {
        if (redisTemplate == null || userId == null || userId.trim().isEmpty()) return;
        redisTemplate.opsForHash().delete(PERMISSION_HASH, userId);
        redisTemplate.opsForHash().delete(ROLE_TYPE_HASH, userId);
    }

    /** 将 Redis 序列化结果转换为稳定的权限 Map。 */
    public static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map)) return null;
        Map<?, ?> source = (Map<?, ?>) value;
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> {
            if (key != null) result.put(String.valueOf(key), item);
        });
        return result;
    }

    /** /sys 是所有登录用户都必须具备的基础读权限，缺失时视为缓存过期。 */
    public static boolean isComplete(Map<String, Object> permissionMap) {
        return permissionMap != null && permissionMap.containsKey("/sys");
    }
}
