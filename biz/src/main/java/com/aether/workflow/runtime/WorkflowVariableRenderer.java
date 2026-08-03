package com.aether.workflow.runtime;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/** 渲染节点提示词和 MCP 参数中的 ${变量名}。 */
public final class WorkflowVariableRenderer {
    private static final Pattern TOKEN = Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}");
    private WorkflowVariableRenderer() { }
    public static String render(String template, Map<String, Object> variables) {
        if (template == null) return null;
        Matcher matcher = TOKEN.matcher(template); StringBuffer output = new StringBuffer();
        while (matcher.find()) { String key = matcher.group(1); if (!variables.containsKey(key)) throw new ServerException(422, I18nUtils.getMessage("workflow.variable.not-provided", new Object[]{key})); matcher.appendReplacement(output, Matcher.quoteReplacement(String.valueOf(variables.get(key)))); }
        matcher.appendTail(output); return output.toString();
    }
}
