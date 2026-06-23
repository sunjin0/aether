package com.aether.agent.security;

import com.aether.exception.ServerException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 工具安全策略校验器。
 */
@Component
public class ToolSecurityValidator {

    private static final Set<String> ALLOWED_METHODS = new HashSet<>(Arrays.asList("GET", "POST"));
    private static final Set<String> FORBIDDEN_HEADERS = new HashSet<>(Arrays.asList(
            "authorization", "cookie", "x-api-key", "x-auth-token"
    ));
    private static final int MAX_RESPONSE_SIZE = 1024 * 1024; // 1MB
    private static final int MAX_URL_LENGTH = 2048;

    /**
     * 校验URL安全性
     */
    public void validateUrl(String url) {
        if (StringUtils.isBlank(url)) {
            throw new ServerException(422, "工具URL为空");
        }

        if (url.length() > MAX_URL_LENGTH) {
            throw new ServerException(422, "工具URL过长");
        }

        try {
            URL parsedUrl = new URL(url);
            String protocol = parsedUrl.getProtocol().toLowerCase();

            // 只允许HTTP/HTTPS协议
            if (!"http".equals(protocol) && !"https".equals(protocol)) {
                throw new ServerException(422, "仅支持HTTP/HTTPS协议");
            }

            // 禁止内网地址
            String host = parsedUrl.getHost().toLowerCase();
            if (isInternalHost(host)) {
                throw new ServerException(403, "禁止访问内网地址");
            }

            // 禁止localhost和127.0.0.1
            if ("localhost".equals(host) || "127.0.0.1".equals(host) || "0.0.0.0".equals(host)) {
                throw new ServerException(403, "禁止访问本地地址");
            }

        } catch (MalformedURLException e) {
            throw new ServerException(422, "工具URL格式错误");
        }
    }

    /**
     * 校验HTTP方法
     */
    public void validateMethod(String method) {
        if (StringUtils.isBlank(method)) {
            throw new ServerException(422, "HTTP方法为空");
        }

        if (!ALLOWED_METHODS.contains(method.toUpperCase())) {
            throw new ServerException(422, "仅支持GET/POST方法");
        }
    }

    /**
     * 校验请求头，过滤敏感头
     */
    public void validateHeaders(Set<String> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }

        for (String header : headers) {
            if (FORBIDDEN_HEADERS.contains(header.toLowerCase())) {
                throw new ServerException(403, "禁止使用敏感请求头: " + header);
            }
        }
    }

    /**
     * 校验响应大小
     */
    public void validateResponseSize(int contentLength) {
        if (contentLength > MAX_RESPONSE_SIZE) {
            throw new ServerException(422, "响应体过大，超过限制(1MB)");
        }
    }

    /**
     * 判断是否为内网地址
     */
    private boolean isInternalHost(String host) {
        // 禁止常见内网段
        return host.startsWith("10.") ||
               host.startsWith("192.168.") ||
               host.startsWith("172.16.") ||
               host.startsWith("172.17.") ||
               host.startsWith("172.18.") ||
               host.startsWith("172.19.") ||
               host.startsWith("172.20.") ||
               host.startsWith("172.21.") ||
               host.startsWith("172.22.") ||
               host.startsWith("172.23.") ||
               host.startsWith("172.24.") ||
               host.startsWith("172.25.") ||
               host.startsWith("172.26.") ||
               host.startsWith("172.27.") ||
               host.startsWith("172.28.") ||
               host.startsWith("172.29.") ||
               host.startsWith("172.30.") ||
               host.startsWith("172.31.") ||
               host.startsWith("169.254.") ||
               host.startsWith("0.");
    }
}
