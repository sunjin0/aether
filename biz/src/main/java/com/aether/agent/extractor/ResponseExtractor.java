package com.aether.agent.extractor;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 响应提取器。
 * 支持JSONPath和正则表达式提取响应内容。
 */
@Component
public class ResponseExtractor {

    /**
     * 从响应中提取内容
     * 
     * @param responseBody 响应体
     * @param extractRule 提取规则（JSONPath或正则）
     * @return 提取的内容
     */
    public String extract(String responseBody, String extractRule) {
        if (StringUtils.isBlank(responseBody)) {
            return responseBody;
        }

        if (StringUtils.isBlank(extractRule)) {
            return responseBody;
        }

        try {
            // 尝试JSONPath提取
            if (extractRule.startsWith("$")) {
                return extractByJsonPath(responseBody, extractRule);
            } else {
                // 正则提取
                return extractByRegex(responseBody, extractRule);
            }
        } catch (Exception e) {
            // 提取失败，返回原始响应
            return responseBody;
        }
    }

    /**
     * 使用JSONPath提取
     */
    private String extractByJsonPath(String responseBody, String jsonPath) {
        try {
            Object result = JsonPath.read(responseBody, jsonPath);
            
            if (result == null) {
                return "";
            }

            if (result instanceof String) {
                return (String) result;
            }

            // 复杂对象转JSON字符串
            return JSON.toJSONString(result);
        } catch (PathNotFoundException e) {
            return "";
        }
    }

    /**
     * 使用正则表达式提取
     */
    private String extractByRegex(String responseBody, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(responseBody);

        if (matcher.find()) {
            // 如果有分组，返回第一个分组
            if (matcher.groupCount() > 0) {
                return matcher.group(1);
            }
            return matcher.group(0);
        }

        return "";
    }
}
