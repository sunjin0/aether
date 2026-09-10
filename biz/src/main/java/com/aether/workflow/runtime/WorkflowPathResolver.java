package com.aether.workflow.runtime;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 工作流统一变量路径解析器。
 *
 * <p>全链路（模板 ${...}、条件表达式、节点输出映射、子流程映射）共用同一套路径语法：</p>
 * <ul>
 *   <li>{@code a}        — 变量 a</li>
 *   <li>{@code a.b.c}    — 按点号逐层取值（Map 键，也允许以点号访问数组下标 {@code list.0.name}）</li>
 *   <li>空路径返回根对象本身</li>
 * </ul>
 *
 * <p>节点自身的输出属于特殊根，由调用方决定以哪个对象作为 {@link #resolve(Object, String)} 的 root。</p>
 */
public final class WorkflowPathResolver {
    /** 匹配 {@code ${a.b.0.c}} 形式的完整引用，段内允许点号分隔与数字下标。 */
    public static final Pattern REFERENCE = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*)}");
    /** 不带花括号的点号路径：a.b.0.c。 */
    private static final String PATH = "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*";
    private static final Pattern INTEGER = Pattern.compile("\\d+");

    private WorkflowPathResolver() {
    }

    /**
     * 沿点号路径取值。
     *
     * @param root 取值根对象（通常为共享变量 map，节点自身输出映射时为其原始输出）
     * @param path 点号路径，支持数字段表示数组下标；null/空串返回 root 本身
     * @return 取值结果；任一段无法导航（类型不符、越界、键缺失）时返回 null
     */
    public static Object resolve(Object root, String path) {
        if (root == null) return null;
        String p = path == null ? "" : path.trim();
        if (p.startsWith("$.")) p = p.substring(2);
        Object current = root;
        if (p.isEmpty()) return current;
        for (String segment : p.split("\\.")) {
            if (segment.isEmpty()) return null;
            current = step(current, segment);
            if (current == null) return null;
        }
        return current;
    }

    /** 单段取值；若取值为 JSON 文本且需继续取值则先解析再取，保证 ${resp.code} 能穿透文本型输出。 */
    private static Object step(Object current, String segment) {
        if (current instanceof Map) {
            return ((Map<?, ?>) current).get(segment);
        }
        if (current instanceof List) {
            if (!INTEGER.matcher(segment).matches()) return null;
            int index = Integer.parseInt(segment);
            if (index < 0 || index >= ((List<?>) current).size()) return null;
            return ((List<?>) current).get(index);
        }
        if (current instanceof Object[]) {
            if (!INTEGER.matcher(segment).matches()) return null;
            int index = Integer.parseInt(segment);
            Object[] array = (Object[]) current;
            if (index < 0 || index >= array.length) return null;
            return array[index];
        }
        if (current instanceof String) {
            // 文本型 JSON 输出：解析后继续取段（参照既有 $json.<path> 语义）。
            Object parsed = parseJsonText((String) current);
            if (parsed == null || parsed instanceof String || parsed instanceof Number || parsed instanceof Boolean)
                return null;
            return step(parsed, segment);
        }
        return null;
    }

    private static Object parseJsonText(String text) {
        try {
            return com.alibaba.fastjson2.JSON.parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 沿点号路径把 value 写入共享变量树（结构化输出目标）。
     *
     * <p>与 {@link #resolve} 同一套路径语法，但目标段要求为变量名（不支持数组下标写入）。
     * 中间缺失的层自动创建为对象逐层下钻；若某中间层已存在但不是对象（标量/数组），
     * 抛出 {@link IllegalArgumentException} 使节点显式失败，避免运行中静默覆盖既有数据。</p>
     *
     * @param root  写入根对象（通常为共享变量 map）
     * @param path  点号路径，如 {@code result.order.total}
     * @param value 写入末段键的值
     * @return root 本身，便于链式调用
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> write(Map<String, Object> root, String path, Object value) {
        if (root == null) return root;
        String p = path == null ? "" : path.trim();
        if (p.startsWith("$.")) p = p.substring(2);
        if (p.isEmpty()) return root;
        String[] segments = p.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < segments.length - 1; i++) {
            String segment = segments[i];
            if (segment.isEmpty()) return root;
            Object child = current.get(segment);
            if (child == null) {
                Map<String, Object> created = new com.alibaba.fastjson2.JSONObject();
                current.put(segment, created);
                current = created;
            } else if (child instanceof Map) {
                current = (Map<String, Object>) child;
            } else {
                throw new IllegalArgumentException("写入嵌套变量目标失败，路径中间层不是对象: " + p);
            }
        }
        String last = segments[segments.length - 1];
        if (!last.isEmpty()) current.put(last, value);
        return root;
    }

    /** 点号路径（不含 $）是否为合法引用路径。 */
    public static boolean isPath(String path) {
        return path != null && path.matches(PATH);
    }
}
