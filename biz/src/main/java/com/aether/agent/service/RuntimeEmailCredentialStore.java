package com.aether.agent.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 仅进程内保存本次运行的调用方邮件凭据，绝不写入数据库。 */
@Component
public class RuntimeEmailCredentialStore {
    private final Map<String, Map<String, Map<String, String>>> values = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Map<String, String>>> pending = new ConcurrentHashMap<>();

    public void putPending(String conversationId, String userId, Map<String, Map<String, String>> secrets) {
        String pendingKey = "pending:" + conversationId + ":" + userId;
        put(pendingKey, userId, secrets);
        pending.put(pendingKey, values.remove(key(pendingKey, userId)));
    }

    public void bindPending(String runId, String conversationId, String userId) {
        Map<String, Map<String, String>> credentialSet = pending.remove("pending:" + conversationId + ":" + userId);
        if (credentialSet != null) values.put(key(runId, userId), credentialSet);
    }

    public void put(String runId, String userId, Map<String, Map<String, String>> secrets) {
        if (StringUtils.isBlank(runId) || StringUtils.isBlank(userId) || secrets == null || secrets.isEmpty()) return;
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : secrets.entrySet()) {
            Map<String, String> item = entry.getValue();
            if (StringUtils.isBlank(entry.getKey()) || item == null || StringUtils.isAnyBlank(item.get("sender_email"), item.get("smtp_authorization_code"))) {
                throw new IllegalArgumentException("runtime_secrets 必须包含 credential_ref、sender_email 和 smtp_authorization_code");
            }
            Map<String, String> credential = new LinkedHashMap<>();
            credential.put("sender_email", item.get("sender_email"));
            credential.put("smtp_authorization_code", item.get("smtp_authorization_code"));
            copy.put(entry.getKey(), credential);
        }
        values.put(key(runId, userId), copy);
    }

    public Map<String, String> get(String runId, String userId, String credentialRef) {
        Map<String, Map<String, String>> byRef = values.get(key(runId, userId));
        Map<String, String> credential = byRef == null ? null : byRef.get(credentialRef);
        return credential == null ? null : new LinkedHashMap<>(credential);
    }

    public Map<String, Map<String, String>> all(String runId, String userId) {
        Map<String, Map<String, String>> source = values.get(key(runId, userId));
        if (source == null) return Collections.emptyMap();
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : source.entrySet()) copy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        return copy;
    }

    public void remove(String runId, String userId) { values.remove(key(runId, userId)); }
    private String key(String runId, String userId) { return runId + ":" + userId; }
}
