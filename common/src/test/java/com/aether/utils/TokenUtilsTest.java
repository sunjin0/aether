package com.aether.utils;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证令牌Utils的行为。
 */
class TokenUtilsTest {
    /**
     * 处理createsEncrypted服务Access令牌WithCustomClaims。
     */
    @Test
    void createsEncryptedServiceAccessTokenWithCustomClaims() {
        Map<String, String> claims = new HashMap<String, String>();
        claims.put("userId", "service-user");
        claims.put("serviceAccountId", "service-account");
        claims.put("serviceTokenVersion", "3");

        String encrypted = TokenUtils.createAccessToken(claims, 120);
        String token = AesUtil.decrypt(encrypted);

        assertEquals("service-user", TokenUtils.getUserId(token));
        assertEquals("service-account", TokenUtils.getClaim(token, "serviceAccountId"));
        assertEquals("3", TokenUtils.getClaim(token, "serviceTokenVersion"));
    }
}
