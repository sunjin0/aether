package com.aether.agent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** 配置部署密钥后，加密保存较为详细的 Agent 审计载荷。 */
@Service
public class AuditDataProtectionService {
    private static final String PREFIX = "ENCv1:";
    private final SecureRandom random = new SecureRandom();
    @Value("${agent.audit.encryption-key:}")
    private String configuredKey;

    public String protect(String value) {
        if (value == null || configuredKey == null || configuredKey.trim().isEmpty()) return value;
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("无法加密 Agent 审计数据", e);
        }
    }

    /**
     * 仅供已通过审计载荷权限校验的管理端读取加密快照。
     * 未启用加密或历史明文记录保持原样返回，便于平滑升级。
     */
    public String unprotect(String value) {
        if (value == null || !value.startsWith(PREFIX)) return value;
        if (configuredKey == null || configuredKey.trim().isEmpty()) {
            throw new IllegalStateException("审计数据已加密，但当前未配置解密密钥");
        }
        try {
            String[] parts = value.split(":", 3);
            if (parts.length != 3) throw new IllegalArgumentException("审计密文格式无效");
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("无法解密 Agent 审计数据", e);
        }
    }

    private byte[] key() throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(configuredKey.getBytes(StandardCharsets.UTF_8));
    }
}
