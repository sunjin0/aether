package com.aether.auth;

/**
 * 用户会话令牌的服务端校验扩展点。
 * common 模块不依赖业务持久化实现，由 biz 模块校验 token 是否仍是当前有效会话。
 */
public interface UserTokenVerifier {
    /**
     * 判断加密后的 access token 是否仍为用户当前有效会话。
     */
    boolean isActive(String userId, String encryptedAccessToken);
}
