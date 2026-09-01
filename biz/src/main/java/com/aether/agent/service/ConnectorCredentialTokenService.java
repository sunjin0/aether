package com.aether.agent.service;

import com.aether.agent.dto.DeepAgentConfig;
import com.alibaba.fastjson2.JSON;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Connector 凭据的短期、范围受限委派令牌。
 *
 * <p>令牌只用于 MCP 请求头，调用方不得把解密后的凭据放入工具参数、Prompt
 * 或审计记录。解密端应在请求作用域内调用 {@link #validate(Map, String, String, String)}。</p>
 */
@Component
public class ConnectorCredentialTokenService {
    private static final long MAX_TTL_SECONDS = 300L;
    private static final int NONCE_LENGTH = 12;
    private final DeepAgentConfig config;

    public ConnectorCredentialTokenService(DeepAgentConfig config) {
        this.config = config;
    }

    public String create(String runId, String userId, String tenantId, String connectorId,
                         List<String> allowedTools, Map<String, String> credential) {
        if (StringUtils.isAnyBlank(runId, userId, tenantId, connectorId)
                || allowedTools == null || allowedTools.isEmpty()
                || credential == null || credential.isEmpty()) {
            throw new IllegalArgumentException("连接器临时凭据参数不完整");
        }
        if (StringUtils.isBlank(config.getMcpCredentialSecret())) {
            throw new IllegalStateException("连接器临时凭据密钥未配置");
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("runId", runId);
            payload.put("userId", userId);
            payload.put("tenantId", tenantId);
            payload.put("connectorId", connectorId);
            payload.put("allowedTools", allowedTools);
            payload.put("credential", new LinkedHashMap<>(credential));
            payload.put("exp", System.currentTimeMillis() / 1000L + MAX_TTL_SECONDS);
            byte[] nonce = new byte[NONCE_LENGTH];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(), "AES"),
                    new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(JSON.toJSONString(payload).getBytes(StandardCharsets.UTF_8));
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return encoder.encodeToString(nonce) + "." + encoder.encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("连接器临时凭据令牌生成失败", e);
        }
    }

    /** 解密令牌；调用方必须随后执行 validate，且不得持久化返回值。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> decrypt(String token) {
        if (StringUtils.isBlank(token) || StringUtils.isBlank(config.getMcpCredentialSecret())) {
            throw new IllegalArgumentException("连接器临时凭据令牌不可用");
        }
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) throw new IllegalArgumentException("令牌格式无效");
            Base64.Decoder decoder = Base64.getUrlDecoder();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(), "AES"),
                    new GCMParameterSpec(128, decoder.decode(parts[0])));
            return JSON.parseObject(new String(cipher.doFinal(decoder.decode(parts[1])), StandardCharsets.UTF_8), Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("连接器临时凭据令牌无效", e);
        }
    }

    public void validate(Map<String, Object> claims, String tenantId, String connectorId, String toolName) {
        if (claims == null || !StringUtils.equals(tenantId, String.valueOf(claims.get("tenantId")))
                || !StringUtils.equals(connectorId, String.valueOf(claims.get("connectorId")))) {
            throw new IllegalArgumentException("连接器临时凭据范围不匹配");
        }
        long exp = Long.parseLong(String.valueOf(claims.get("exp")));
        if (exp <= System.currentTimeMillis() / 1000L) throw new IllegalArgumentException("连接器临时凭据已过期");
        Object tools = claims.get("allowedTools");
        if (!(tools instanceof List) || !((List<?>) tools).contains(toolName)) {
            throw new IllegalArgumentException("连接器工具未获授权");
        }
    }

    private byte[] key() throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(
                config.getMcpCredentialSecret().getBytes(StandardCharsets.UTF_8));
    }
}
