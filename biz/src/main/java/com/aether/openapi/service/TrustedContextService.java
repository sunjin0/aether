package com.aether.openapi.service;

import com.aether.agent.product.entity.AgentProductProfile;
import com.aether.exception.ServerException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aether.utils.AesUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Validates the narrow, server-stored business context accepted by an Agent product. */
@Service
public class TrustedContextService {
    private static final int MAX_VALUE_LENGTH = 256;

    /**
     * Merges a request context into the durable context. A product without an
     * explicit declaration accepts no context, preventing an accidental JSON
     * pass-through from becoming a prompt or tool authorization channel.
     */
    public String merge(AgentProductProfile product, String existingJson, Map<String, Object> input) {
        Map<String, Object> incoming = input == null ? Collections.<String, Object>emptyMap() : input;
        Map<String, JSONObject> rules = rules(product == null ? null : product.getAllowedContextKeys());
        if (incoming.isEmpty()) return StringUtils.isBlank(existingJson) ? encode(new JSONObject()) : existingJson;
        if (rules.isEmpty()) throw invalid("未声明允许的 context 键");

        JSONObject existing = decode(existingJson);
        Map<String, Object> merged = new TreeMap<String, Object>();
        merged.putAll(existing);
        for (Map.Entry<String, Object> entry : incoming.entrySet()) {
            JSONObject rule = rules.get(entry.getKey());
            if (rule == null) throw invalid("不允许的 context 键: " + entry.getKey());
            Object value = entry.getValue();
            validateValue(entry.getKey(), value, rule.getString("type"));
            Object old = existing.get(entry.getKey());
            if (old != null && Boolean.TRUE.equals(rule.getBoolean("immutable")) && !JSON.toJSONString(old).equals(JSON.toJSONString(value)))
                throw invalid("不可变 context 键不能变更: " + entry.getKey());
            merged.put(entry.getKey(), value);
        }
        return encode(new JSONObject(new LinkedHashMap<String, Object>(merged)));
    }

    /** Used only by server-side tool/runtime code; callers must never return this value to clients. */
    public Map<String, Object> read(String stored) {
        return new LinkedHashMap<String, Object>(decode(stored));
    }

    private JSONObject decode(String stored) {
        if (StringUtils.isBlank(stored)) return new JSONObject();
        String json = stored.startsWith("v1:") ? AesUtil.decrypt(stored.substring(3)) : stored;
        try {
            return JSON.parseObject(json);
        } catch (RuntimeException ex) {
            throw invalid("已保存的 context 无法解析");
        }
    }

    private String encode(JSONObject value) {
        return "v1:" + AesUtil.encrypt(value.toJSONString());
    }

    private Map<String, JSONObject> rules(String declaration) {
        if (StringUtils.isBlank(declaration)) return Collections.emptyMap();
        try {
            Map<String, JSONObject> result = new LinkedHashMap<String, JSONObject>();
            if (declaration.trim().startsWith("[")) {
                JSONArray keys = JSON.parseArray(declaration);
                for (int i = 0; i < keys.size(); i++) {
                    String key = keys.getString(i);
                    if (StringUtils.isNotBlank(key)) result.put(key, new JSONObject());
                }
            } else {
                JSONObject object = JSON.parseObject(declaration);
                for (String key : object.keySet()) {
                    Object value = object.get(key);
                    result.put(key, value instanceof JSONObject ? (JSONObject) value : new JSONObject());
                }
            }
            return result;
        } catch (RuntimeException ex) {
            throw invalid("产品 context 声明无效");
        }
    }

    private void validateValue(String key, Object value, String type) {
        if (value == null) throw invalid("context 值不能为空: " + key);
        String actual = value instanceof String ? "string" : value instanceof Boolean ? "boolean"
                : value instanceof Number ? "number" : null;
        if (actual == null) throw invalid("context 仅支持 string、number 或 boolean: " + key);
        if (StringUtils.isNotBlank(type) && !type.equalsIgnoreCase(actual)) throw invalid("context 类型不匹配: " + key);
        if (value.toString().length() > MAX_VALUE_LENGTH) throw invalid("context 值过长: " + key);
    }

    private ServerException invalid(String message) {
        return new ServerException(422, "TRUSTED_CONTEXT_INVALID: " + message);
    }
}
