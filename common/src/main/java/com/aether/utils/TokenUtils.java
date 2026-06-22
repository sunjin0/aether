package com.aether.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.aether.entity.Token;
import com.aether.exception.ServerException;

import java.util.Calendar;
import java.util.Map;

/**
 * 令牌实用程序
 *
 * @author sun
 * @since 2024/09/20
 */

public class TokenUtils {
    // 签名密钥
    private static final String SECRET_KEY = "1sa(s}>s.@jj,asj.!hg5454";
    public static final String TOKEN_KEY = "TokenList";
    /**
     * 创建令牌
     *
     * @param payload 有效载荷
     * @return {@link Token }
     */
    public static Token createToken(Map<String, String> payload) {
        if (payload == null || payload.get("userId").isEmpty()) {
            throw new ServerException(400, "用户ID不能为空");
        }
        // 指定token过期时间为2小时
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR, 6);
        JWTCreator.Builder builder = JWT.create();
        // 构建payload
        payload.forEach(builder::withClaim);
        // 指定过期时间和签名算法
        String token = builder.withExpiresAt(calendar.getTime()).sign(Algorithm.HMAC256(SECRET_KEY));

        // 返回token
        return getToken(payload, token, createRefreshToken(payload));

    }

    /**
     * 创建刷新令牌
     *
     * @param payload 有效载荷
     * @return {@link Token }
     */
    public static String createRefreshToken(Map<String, String> payload) {

        // 指定刷新token过期时间为7天
        Calendar calendar = Calendar.getInstance();
        JWTCreator.Builder builder = JWT.create();
        calendar.add(Calendar.DATE, 7);
        // 构建payload
        payload.forEach(builder::withClaim);
        // 构建刷新token
        return builder.withExpiresAt(calendar.getTime()).sign(Algorithm.HMAC256(SECRET_KEY));
    }

    /**
     * 解析token
     *
     * @param token token字符串
     * @return 解析后的token
     */
    private static DecodedJWT decode(String token) {
        JWTVerifier jwtVerifier = JWT.require(Algorithm.HMAC256(SECRET_KEY)).build();
        return jwtVerifier.verify(token);
    }

    /**
     * 获取用户 ID
     *
     * @param token 令 牌
     * @return {@link String }
     */
    public static String getUserId(String token) {
        DecodedJWT decode = decode(token);
        return decode.getClaim("userId").asString();
    }

    /**
     * 是否过期
     *
     * @param token 令 牌
     * @return boolean true 已过期，false 未过期
     */
    public static boolean isExpired(String token) {
        DecodedJWT decode = decode(token);
        return decode.getExpiresAt().before(Calendar.getInstance().getTime());
    }

    /**
     * 获取 令牌
     *
     * @param payload      有效载荷
     * @param token        令 牌
     * @param refreshToken 刷新令牌
     * @return {@link Token }
     */
    private static Token getToken(Map<String, String> payload, String token, String refreshToken) {
        Token Token = new Token();
        Token.setUserId(payload.get("userId"));
        //加密令牌
        Token.setToken(AesUtil.encrypt(token));
        //加密刷新令牌
        Token.setRefreshToken(AesUtil.encrypt(refreshToken));
        Token.setState(1);
        return Token;
    }
}
