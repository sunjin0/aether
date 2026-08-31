package com.aether.agent.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 将工具结果压缩为适合下一轮模型调用的结构化上下文。
 * 原始结果仍由工具消息和审计记录保存，此处只控制模型上下文大小。
 */
@Component
public class ToolResultContextCompressor {
    private static final int MAX_CONTEXT_CHARS = 6000;
    private static final int SEARCH_SAMPLE_SIZE = 5;
    private static final int LIST_SAMPLE_SIZE = 10;
    private static final int TABLE_SAMPLE_SIZE = 20;

    /**
     * 根据工具业务类型压缩结果；未知类型仍按通用 JSON 与文本策略处理。
     */
    public String compact(String toolType, String content) {
        if (StringUtils.isBlank(content) || content.length() <= MAX_CONTEXT_CHARS) return content;
        String normalizedType = StringUtils.defaultString(toolType).toLowerCase();
        try {
            Object parsed = JSON.parse(content);
            if (parsed instanceof JSONObject) {
                return compactObject(normalizedType, (JSONObject) parsed);
            }
            if (parsed instanceof JSONArray) {
                return compactArray(normalizedType, (JSONArray) parsed);
            }
        } catch (RuntimeException ignored) {
            // 非 JSON 文本继续采用文档或日志压缩策略。
        }
        return compactText(normalizedType, content);
    }

    /** 处理对象型 JSON，并移除调试、链路追踪与原始大字段。 */
    private String compactObject(String toolType, JSONObject source) {
        JSONObject compact = new JSONObject();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            if (isDiagnosticField(key)) continue;
            Object value = entry.getValue();
            if (value instanceof JSONArray) {
                compact.put(key, sampleArray(toolType, key, (JSONArray) value));
            } else if (isLargeTextField(key, value)) {
                compact.put(key, abbreviate(String.valueOf(value), 1200));
            } else {
                compact.put(key, value);
            }
        }
        return ensureLimit("[" + compressionLabel(toolType) + "，已移除调试字段]", compact.toJSONString());
    }

    /** 处理根数组结果，并按搜索、列表或表格结果保留合适的样本数量。 */
    private String compactArray(String toolType, JSONArray source) {
        JSONObject sample = sampleArray(toolType, "items", source);
        JSONObject envelope = new JSONObject();
        envelope.put("total", source.size());
        envelope.put("items", sample);
        return ensureLimit("[" + compressionLabel(toolType) + "]", envelope.toJSONString());
    }

    /** 数组保留高价值样本，同时明确返回总数以便模型决定是否继续分页读取。 */
    private JSONObject sampleArray(String toolType, String key, JSONArray source) {
        int limit = sampleSize(toolType, key);
        JSONArray sample = new JSONArray();
        for (int i = 0; i < Math.min(limit, source.size()); i++) {
            Object item = source.get(i);
            sample.add(compactArrayItem(item));
        }
        JSONObject result = new JSONObject();
        result.put("total", source.size());
        result.put("sample", sample);
        result.put("truncated", source.size() > sample.size());
        return result;
    }

    /** 单个结果项移除噪声字段，并限制正文片段避免一项吞掉全部预算。 */
    private Object compactArrayItem(Object item) {
        if (!(item instanceof JSONObject)) return item;
        JSONObject compact = new JSONObject();
        for (Map.Entry<String, Object> entry : ((JSONObject) item).entrySet()) {
            if (isDiagnosticField(entry.getKey())) continue;
            Object value = entry.getValue();
            compact.put(entry.getKey(), isLargeTextField(entry.getKey(), value)
                    ? abbreviate(String.valueOf(value), 800) : value);
        }
        return compact;
    }

    /** 文档正文保留首尾片段，日志保留首尾行，避免丢失结论和错误尾部。 */
    private String compactText(String toolType, String content) {
        int head = "document".equals(toolType) ? 4200 : 3600;
        int tail = MAX_CONTEXT_CHARS - head;
        if (content.length() <= MAX_CONTEXT_CHARS) return content;
        return "[" + compressionLabel(toolType) + "，原始长度 " + content.length() + " 字符]\n"
                + content.substring(0, head)
                + "\n...[已省略 " + (content.length() - head - tail) + " 字符]...\n"
                + content.substring(content.length() - tail);
    }

    /** 压缩后仍超限时执行统一的首尾兜底，保证不会撑爆下一轮模型上下文。 */
    private String ensureLimit(String prefix, String value) {
        if (prefix.length() + value.length() <= MAX_CONTEXT_CHARS) return prefix + "\n" + value;
        int budget = MAX_CONTEXT_CHARS - prefix.length() - 48;
        int head = budget * 3 / 4;
        return prefix + "\n" + value.substring(0, head) + "...[结构化结果已截断]..."
                + value.substring(value.length() - (budget - head));
    }

    private int sampleSize(String toolType, String key) {
        if ("search".equals(toolType) || "knowledge".equals(toolType) || key.toLowerCase().contains("hit")) return SEARCH_SAMPLE_SIZE;
        if ("sql".equals(toolType) || "analytics".equals(toolType) || "statistics".equals(toolType)) return TABLE_SAMPLE_SIZE;
        return LIST_SAMPLE_SIZE;
    }

    private boolean isDiagnosticField(String key) {
        String normalized = StringUtils.defaultString(key).toLowerCase();
        return normalized.equals("debug") || normalized.equals("metadata") || normalized.equals("trace")
                || normalized.equals("logs") || normalized.equals("rawresponse") || normalized.equals("stacktrace");
    }

    private boolean isLargeTextField(String key, Object value) {
        if (value == null || !(value instanceof String)) return false;
        String normalized = StringUtils.defaultString(key).toLowerCase();
        return String.valueOf(value).length() > 1200 || normalized.equals("content") || normalized.equals("body")
                || normalized.equals("text") || normalized.equals("document");
    }

    private String compressionLabel(String toolType) {
        if ("search".equals(toolType) || "knowledge".equals(toolType)) return "搜索结果已保留总数和高相关样本";
        if ("sql".equals(toolType) || "analytics".equals(toolType) || "statistics".equals(toolType)) return "统计结果已保留字段、总数和前若干行";
        if ("document".equals(toolType)) return "文档结果已保留正文首尾片段";
        if ("list".equals(toolType)) return "列表结果已保留总数和前若干项";
        return "工具 JSON 结果已结构化压缩";
    }

    private String abbreviate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "…";
    }
}
