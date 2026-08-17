package com.aether.workflow.runtime;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 仅用于审计副本与外发回调的字段级脱敏，不改变运行时共享变量。
 */
@Component
public class WorkflowSensitiveDataSanitizer {
    private final Set<String> sensitiveFields;

    /**
     * 创建 {@code WorkflowSensitiveDataSanitizer} 实例。
     */
    public WorkflowSensitiveDataSanitizer(@Value("${aether.workflow.security.mask-fields:password,secret,token,authorization,access_token,refresh_token}") String fields) {
        Set<String> result = new HashSet<String>();
        for (String field : fields.split(","))
            if (!field.trim().isEmpty()) result.add(field.trim().toLowerCase(Locale.ROOT));
        this.sensitiveFields = result;
    }

    /**
     * 清理敏感信息Json。
     */
    public String sanitizeJson(String text) {
        if (text == null || text.isEmpty()) return text;
        try {
            return JSON.toJSONString(sanitize(JSON.parse(text)));
        } catch (Exception ignored) {
            return text;
        }
    }

    /**
     * 清理敏感信息当前请求。
     */
    @SuppressWarnings("unchecked")
    private Object sanitize(Object value) {
        if (value instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                String key = String.valueOf(entry.getKey());
                result.put(key, sensitiveFields.contains(key.toLowerCase(Locale.ROOT)) ? "***" : sanitize(entry.getValue()));
            }
            return result;
        }
        if (value instanceof Collection) {
            List<Object> result = new ArrayList<Object>();
            for (Object item : (Collection<?>) value) result.add(sanitize(item));
            return result;
        }
        return value;
    }
}
