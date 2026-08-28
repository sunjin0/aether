package com.aether.utils;

import com.aether.entity.Token;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertTrue(TokenUtils.hasTokenType(token, TokenUtils.ACCESS_TOKEN_TYPE));
    }

    @Test
    void distinguishesAccessAndRefreshTokens() {
        Map<String, String> claims = new HashMap<String, String>();
        claims.put("userId", "user-1");

        Token token = TokenUtils.createToken(claims);
        String accessToken = AesUtil.decrypt(token.getToken());
        String refreshToken = AesUtil.decrypt(token.getRefreshToken());

        assertTrue(TokenUtils.hasTokenType(accessToken, TokenUtils.ACCESS_TOKEN_TYPE));
        assertFalse(TokenUtils.hasTokenType(accessToken, TokenUtils.REFRESH_TOKEN_TYPE));
        assertTrue(TokenUtils.hasTokenType(refreshToken, TokenUtils.REFRESH_TOKEN_TYPE));
    }
}
