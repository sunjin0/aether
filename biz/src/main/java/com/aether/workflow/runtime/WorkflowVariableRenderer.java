package com.aether.workflow.runtime;

import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;

import java.util.Map;
import java.util.regex.Matcher;

/**
 * 渲染节点提示词、HTTP 参数等模板中的 ${路径} 引用。
 * <p>引用支持点号路径与数字下标，例如 ${orderId}、${order.total}、${items.0.price}；
 * 未提供根变量时抛出 422（与数据流校验一致），深层字段缺失时渲染为 null。</p>
 */
public final class WorkflowVariableRenderer {
    private WorkflowVariableRenderer() {
    }

    /**
     * 渲染模板，将 ${路径} 替换为变量值。
     *
     * @param template  模板文本
     * @param variables 共享变量上下文
     * @return 渲染结果
     */
    public static String render(String template, Map<String, Object> variables) {
        if (template == null) return null;
        Matcher matcher = WorkflowPathResolver.REFERENCE.matcher(template);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String path = matcher.group(1);
            String root = rootSegment(path);
            if (!variables.containsKey(root))
                throw new ServerException(422, I18nUtils.getMessage("workflow.variable.not-provided", new Object[]{root}));
            matcher.appendReplacement(output, Matcher.quoteReplacement(String.valueOf(WorkflowPathResolver.resolve(variables, path))));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    /** 取点号路径的第一个根段。 */
    private static String rootSegment(String path) {
        int dot = path.indexOf('.');
        return dot < 0 ? path : path.substring(0, dot);
    }
}
