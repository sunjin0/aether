package com.aether.governance.service.impl;

import com.aether.governance.service.SecretProvider;
import com.aether.utils.AesUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Development local provider. Values are encrypted before being held in memory;
 * production deployments should replace this bean with Vault/Kubernetes adapters.
 */
@Service
@ConditionalOnProperty(name = "aether.secret.provider", havingValue = "local", matchIfMissing = true)
public class LocalSecretProvider implements SecretProvider {
    private final Map<String, Map<String, String>> store = new ConcurrentHashMap<>();

    @Override
    public Map<String, String> resolve(String credentialRef, String subjectType, String subjectId) {
        Map<String, String> encrypted = store.get(key(credentialRef, subjectType, subjectId));
        if (encrypted == null) return Collections.emptyMap();
        Map<String, String> result = new LinkedHashMap<>();
        encrypted.forEach((name, value) -> result.put(name, AesUtil.decrypt(value)));
        return result;
    }

    @Override
    public String put(String credentialRef, String subjectType, String subjectId, Map<String, String> values) {
        if (StringUtils.isAnyBlank(credentialRef, subjectType, subjectId) || values == null || values.isEmpty())
            throw new IllegalArgumentException("credentialRef、subject 和 values 不能为空");
        Map<String, String> encrypted = new LinkedHashMap<>();
        values.forEach((name, value) -> {
            if (StringUtils.isBlank(name) || value == null) throw new IllegalArgumentException("Secret 名称和值不能为空");
            encrypted.put(name, AesUtil.encrypt(value));
        });
        store.put(key(credentialRef, subjectType, subjectId), encrypted);
        return credentialRef;
    }

    @Override
    public void revoke(String credentialRef, String subjectType, String subjectId) {
        store.remove(key(credentialRef, subjectType, subjectId));
    }

    private String key(String credentialRef, String subjectType, String subjectId) {
        return subjectType + ":" + subjectId + ":" + credentialRef;
    }
}
