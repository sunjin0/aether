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
import java.util.Map;

/** 签发仅供 MCP 解密的短期 AES-GCM 邮件凭据令牌。 */
@Component
public class EmailCredentialTokenService {
    private final DeepAgentConfig config;
    public EmailCredentialTokenService(DeepAgentConfig config) { this.config = config; }

    public String create(String runId, String userId, String credentialRef, Map<String, String> credential) {
        if (StringUtils.isBlank(config.getMcpCredentialSecret()) || credential == null) throw new IllegalArgumentException("邮件临时凭据不可用");
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("runId", runId); payload.put("userId", userId); payload.put("credentialRef", credentialRef);
            payload.put("credential", credential); payload.put("exp", System.currentTimeMillis() / 1000 + 300);
            byte[] nonce = new byte[12]; new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(JSON.toJSONString(payload).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(nonce) + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        } catch (Exception e) { throw new IllegalStateException("邮件临时凭据令牌生成失败", e); }
    }
    private byte[] key() throws Exception { return MessageDigest.getInstance("SHA-256").digest(config.getMcpCredentialSecret().getBytes(StandardCharsets.UTF_8)); }
}
