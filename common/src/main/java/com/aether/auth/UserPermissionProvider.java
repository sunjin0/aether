package com.aether.auth;

import java.util.Map;

/**
 * 为认证层提供可持久化的用户权限快照。
 *
 * <p>common 只依赖这个扩展点，不直接依赖 biz 的用户、角色表。认证请求
 * 在 Redis 中找不到权限快照时，可以通过该接口从持久化存储重建快照，
 * 因此应用重启或 Redis 缓存短暂丢失都不要求用户重新登录。</p>
 */
public interface UserPermissionProvider {

    /**
     * 根据用户 ID 重建权限快照。
     *
     * @param userId              用户 ID
     * @param encryptedAccessToken 加密后的访问令牌；用于恢复 /sys 读权限
     * @return 路径到读写权限的映射
     */
    Map<String, Object> loadPermissionMap(String userId, String encryptedAccessToken);

    /**
     * 读取当前用户的稳定角色类型。
     *
     * @param userId 用户 ID
     * @return ADMIN 或 USER
     */
    String loadRoleType(String userId);
}
