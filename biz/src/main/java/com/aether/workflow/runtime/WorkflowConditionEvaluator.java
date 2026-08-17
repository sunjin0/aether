package com.aether.workflow.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作流条件表达式求值器。
 * <p>支持的表达式语法：</p>
 * <ul>
 *   <li>{@code ${var} == "value"}  — 字符串相等</li>
 *   <li>{@code ${var} != "value"}  — 字符串不等</li>
 *   <li>{@code ${var} > 80}        — 数值大于</li>
 *   <li>{@code ${var} >= 80}       — 数值大于等于</li>
 *   <li>{@code ${var} < 100}       — 数值小于</li>
 *   <li>{@code ${var} <= 100}      — 数值小于等于</li>
 *   <li>{@code ${var} contains "text"} — 字符串包含</li>
 *   <li>{@code ${var}}             — 真值判断（非 null、非空字符串、非 false、非 0）</li>
 *   <li>多个表达式可用 {@code &&} 与 {@code ||} 组合，{@code &&} 优先级高于 {@code ||}</li>
 * </ul>
 */
public final class WorkflowConditionEvaluator {
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)}");
    // 匹配: ${var} op value  或  ${var} op "quoted value"
    private static final Pattern EXPR_PATTERN = Pattern.compile(
            "\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)}\\s*(==|!=|>=|<=|>|<|contains)\\s*(?:\"([^\"]*)\"|(\\S+))");

    /**
     * 创建 {@code WorkflowConditionEvaluator} 实例。
     */
    private WorkflowConditionEvaluator() {
    }

    /**
     * 求值条件表达式，支持 && 与 || 复合。
     *
     * @param expression 条件表达式
     * @param variables  工作流变量上下文
     * @return 表达式是否为真
     */
    public static boolean evaluate(String expression, Map<String, Object> variables) {
        if (expression == null || expression.trim().isEmpty()) return true;
        String expr = expression.trim();

        // && 优先级高于 ||：先按 || 分组，组内按 && 求值
        List<String> orGroups = splitTopLevel(expr, "||");
        for (String group : orGroups) {
            List<String> andParts = splitTopLevel(group, "&&");
            boolean groupResult = true;
            for (String part : andParts) {
                if (!evaluateSingle(part.trim(), variables)) {
                    groupResult = false;
                    break;
                }
            }
            if (groupResult) return true;
        }
        return false;
    }

    /**
     * 求值单条比较表达式或真值判断。
     */
    private static boolean evaluateSingle(String expr, Map<String, Object> variables) {
        Matcher m = EXPR_PATTERN.matcher(expr);
        if (m.matches()) {
            String varName = m.group(1);
            String operator = m.group(2);
            String strRight = m.group(3); // 带引号的字符串值
            String rawRight = m.group(4); // 不带引号的值
            Object leftValue = variables.get(varName);
            String rightStr = strRight != null ? strRight : rawRight;

            return applyOperator(leftValue, operator, rightStr);
        }

        // 无运算符 → 真值判断
        Matcher varMatcher = VAR_PATTERN.matcher(expr);
        if (varMatcher.matches()) {
            Object value = variables.get(varMatcher.group(1));
            return isTruthy(value);
        }

        // 配置错误不能被解释成真值，否则会静默走入错误分支。
        return false;
    }

    /**
     * 按运算符切分表达式，跳过引号内的内容。
     * 例如 "a && b" → ["a ", " b"]。
     */
    private static List<String> splitTopLevel(String expr, String op) {
        List<String> parts = new ArrayList<String>();
        boolean inQuote = false;
        int start = 0;
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (c == '"') {
                if (i > 0 && expr.charAt(i - 1) == '\\') {
                    i++;
                    continue;
                }
                inQuote = !inQuote;
            } else if (!inQuote && c == op.charAt(0) && expr.startsWith(op, i)) {
                parts.add(expr.substring(start, i));
                i += op.length();
                start = i;
                continue;
            }
            i++;
        }
        parts.add(expr.substring(start));
        return parts;
    }

    /**
     * 处理applyOperator。
     */
    private static boolean applyOperator(Object left, String operator, String rightStr) {
        switch (operator) {
            case "==":
                if (left == null) return rightStr == null || "null".equals(rightStr);
                return compareAsString(left).equals(rightStr);
            case "!=":
                if (left == null) return rightStr != null && !"null".equals(rightStr);
                return !compareAsString(left).equals(rightStr);
            case ">":
            case ">=":
            case "<":
            case "<=":
                return compareNumeric(left, operator, rightStr);
            case "contains":
                if (left == null || rightStr == null) return false;
                return compareAsString(left).toLowerCase().contains(rightStr.toLowerCase());
            default:
                return false;
        }
    }

    /**
     * 处理compareNumeric。
     */
    private static boolean compareNumeric(Object left, String operator, String rightStr) {
        Double leftNum = toDouble(left);
        Double rightNum = toDouble(rightStr);
        if (leftNum == null || rightNum == null) return false;
        int cmp = leftNum.compareTo(rightNum);
        switch (operator) {
            case ">":
                return cmp > 0;
            case ">=":
                return cmp >= 0;
            case "<":
                return cmp < 0;
            case "<=":
                return cmp <= 0;
            default:
                return false;
        }
    }

    /**
     * 处理toDouble。
     */
    private static Double toDouble(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 处理compareAsString。
     */
    private static String compareAsString(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }

    /**
     * 判断是否为Truthy。
     */
    private static boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0;
        if (value instanceof String) return !((String) value).isEmpty() && !"false".equalsIgnoreCase((String) value);
        return true;
    }
}
