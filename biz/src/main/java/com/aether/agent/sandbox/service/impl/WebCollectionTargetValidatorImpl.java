package com.aether.agent.sandbox.service.impl;

import com.aether.agent.sandbox.service.WebCollectionTargetValidator;
import com.aether.exception.ServerException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.util.*;

/**
 * Control-plane guard for collection targets. Connection-time DNS/IP revalidation belongs to the egress proxy.
 */
@Component
public class WebCollectionTargetValidatorImpl implements WebCollectionTargetValidator {
    /**
     * 校验当前请求。
     */
    @Override
    public void validate(Map<String, Object> input, Map<String, Object> config) {
        if (!"WEB_COLLECTION".equals(String.valueOf(config.get("executionMode")))) return;
        String url = input == null ? null : String.valueOf(input.get("targetUrl"));
        if (StringUtils.isBlank(url)) throw bad("web collection targetUrl is required");
        String purpose = input.get("purpose") instanceof String ? (String) input.get("purpose") : null;
        if (StringUtils.isBlank(purpose) || purpose.length() > 512)
            throw bad("web collection purpose is required and must be at most 512 characters");
        final URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            throw bad("web collection targetUrl is invalid");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || StringUtils.isNotBlank(uri.getUserInfo()) || StringUtils.isBlank(uri.getHost()) || (uri.getPort() != -1 && uri.getPort() != 443))
            throw bad("web collection target must be HTTPS on port 443");
        String host;
        try {
            host = IDN.toASCII(uri.getHost()).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            throw bad("web collection host is invalid");
        }
        if (isIpLiteral(host) || isBlockedHost(host)) throw bad("web collection target host is blocked");
        List<String> allowed = parseDomains(config.get("allowedDomains"));
        boolean subdomains = Boolean.TRUE.equals(config.get("allowSubdomains"));
        if (allowed.isEmpty() || !allowed.stream().anyMatch(domain -> host.equals(domain) || (subdomains && host.endsWith("." + domain))))
            throw bad("web collection target host is not allowlisted");
        Object requested = input.get("estimatedRequests");
        int limit = number(config.get("maxRequests"), 0);
        if (!(requested instanceof Number) || ((Number) requested).intValue() < 1 || (limit > 0 && ((Number) requested).intValue() > limit))
            throw bad("web collection estimatedRequests must be within the template request quota");
        Object depth = input.get("pageDepth");
        int maxDepth = number(config.get("maxPageDepth"), 0);
        if (depth != null && (!(depth instanceof Number) || ((Number) depth).intValue() < 0 || (maxDepth > 0 && ((Number) depth).intValue() > maxDepth)))
            throw bad("web collection pageDepth exceeds the template quota");
    }

    /**
     * 解析Domains。
     */
    private List<String> parseDomains(Object value) {
        try {
            List<String> result = com.alibaba.fastjson2.JSON.parseArray(com.alibaba.fastjson2.JSON.toJSONString(value), String.class);
            return result == null ? Collections.emptyList() : result;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 判断是否为IpLiteral。
     */
    private boolean isIpLiteral(String host) {
        try {
            return host.matches("[0-9a-fA-F:.]+") && InetAddress.getByName(host) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断是否为BlockedHost。
     */
    private boolean isBlockedHost(String host) {
        return "localhost".equals(host) || host.endsWith(".localhost") || host.endsWith(".local") || host.equals("metadata.google.internal");
    }

    /**
     * 处理number。
     */
    private int number(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    /**
     * 处理bad。
     */
    private ServerException bad(String message) {
        return new ServerException(400, message);
    }
}
