package com.aether.auth;

/**
 * 可选的服务账号令牌校验扩展点。
 * common 模块不依赖业务持久化实现，由 biz 模块提供账号状态与版本的即时校验。
 */
public interface ServiceTokenVerifier {
    boolean isActive(String serviceAccountId, String tokenVersion);
}
