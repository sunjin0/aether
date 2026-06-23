package com.aether.agent.template;

import com.alibaba.fastjson2.JSON;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板渲染工具。
 * 支持使用 ${paramName} 占位符渲染请求头和请求体。
 */
@Component
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    /**
     * 渲染模板，将占位符替换为实际参数值
     */
    public String render(String template, Map<String, Object> arguments) {
        if (StringUtils.isBlank(template)) {
            return template;
        }

        if (arguments == null || arguments.isEmpty()) {
            return template;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String paramName = matcher.group(1);
            Object value = arguments.get(paramName);
            String replacement = value != null ? value.toString() : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 渲染JSON模板
     */
    public String renderJson(String jsonTemplate, Map<String, Object> arguments) {
        String rendered = render(jsonTemplate, arguments);
        
        // 验证JSON格式
        try {
            JSON.parse(rendered);
            return rendered;
        } catch (Exception e) {
            // 如果不是有效JSON，直接返回渲染后的字符串
            return rendered;
        }
    }
}
