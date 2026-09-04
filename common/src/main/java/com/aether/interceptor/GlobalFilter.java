package com.aether.interceptor;

import com.alibaba.fastjson2.JSON;
import com.aether.entity.WebResponse;
import com.aether.auth.ServiceTokenVerifier;
import com.aether.auth.UserTokenVerifier;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.utils.AesUtil;
import com.aether.utils.TokenUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.beans.factory.ObjectProvider;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

/**
 * 全局过滤器（替代GlobalInterceptor）
 * <p>
 * 解决的问题：
 * 1. ThreadLocal内存泄漏 - 使用try-finally确保清理
 * 2. NPE风险 - 增加null检查
 * 3. Token校验不完整 - 统一捕获所有认证异常
 * 4. 请求日志完善 - 支持POST请求体记录
 *
 * @author sun
 * @since 2024/09/20
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GlobalFilter.class);
    private static final String PRINCIPAL_TYPE_SERVICE_ACCOUNT = "SERVICE_ACCOUNT";
    private final ObjectProvider<ServiceTokenVerifier> serviceTokenVerifierProvider;
    private final ObjectProvider<UserTokenVerifier> userTokenVerifierProvider;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    /**
     * 创建 {@code GlobalFilter} 实例。
     */
    public GlobalFilter(ObjectProvider<ServiceTokenVerifier> serviceTokenVerifierProvider,
                        ObjectProvider<UserTokenVerifier> userTokenVerifierProvider,
                        ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.serviceTokenVerifierProvider = serviceTokenVerifierProvider;
        this.userTokenVerifierProvider = userTokenVerifierProvider;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    /**
     * 处理doFilterInternal。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        HashMap<String, String> payload = new HashMap<>();
        String traceId = request.getHeader("X-Trace-Id");
        String spanId = null;
        String traceparent = request.getHeader("traceparent");
        if (traceparent != null && traceparent.matches("00-[0-9a-fA-F]{32}-[0-9a-fA-F]{16}-[0-9a-fA-F]{2}")) {
            String[] parts = traceparent.split("-");
            if (!parts[1].matches("0{32}") && !parts[2].matches("0{16}")) {
                traceId = parts[1].toLowerCase();
                spanId = parts[2].toLowerCase();
            }
        }
        if (traceId == null || !traceId.matches("[A-Za-z0-9_-]{8,128}")) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        response.setHeader("X-Trace-Id", traceId);
        if (spanId == null) spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        response.setHeader("traceparent", "00-" + normalizeTraceId(traceId) + "-" + spanId + "-01");
        MDC.put("traceId", traceId);
        MDC.put("spanId", spanId);

        try {
            // 认证处理
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                try {
                    String encryptedToken = authorization.substring("Bearer ".length());
                    String token = AesUtil.decrypt(encryptedToken);
                    // 校验token（签名+过期时间）
                    String userId = TokenUtils.getUserId(token);
                    String principalType = TokenUtils.getClaim(token, "principalType");
                    String principalId = TokenUtils.getClaim(token, "principalId");
                    String serviceAccountId = TokenUtils.getClaim(token, "serviceAccountId");
                    String applicationId = TokenUtils.getClaim(token, "applicationId");
                    String tenantId = TokenUtils.getClaim(token, "tenantId");
                    if (serviceAccountId != null && !serviceAccountId.isEmpty()) {
                        ServiceTokenVerifier verifier = serviceTokenVerifierProvider.getIfAvailable();
                        String tokenVersion = TokenUtils.getClaim(token, "serviceTokenVersion");
                        if (verifier == null || !verifier.isActive(serviceAccountId, tokenVersion)) {
                            throw new ServerException(401, I18nUtils.getMessage("service-account.token.expired"));
                        }
                        if (!isServiceAccountPathAllowed(request.getRequestURI())) {
                            throw new ServerException(403, I18nUtils.getMessage("auth.error.no.permission"));
                        }
                        payload.put("principalType", PRINCIPAL_TYPE_SERVICE_ACCOUNT);
                        payload.put("principalId", principalId != null && !principalId.isEmpty() ? principalId : userId);
                        payload.put("serviceAccountId", serviceAccountId);
                        if (applicationId != null && !applicationId.isEmpty()) payload.put("applicationId", applicationId);
                        if (tenantId != null && !tenantId.isEmpty()) payload.put("tenantId", tenantId);
                    } else {
                        if (!TokenUtils.hasTokenType(token, TokenUtils.ACCESS_TOKEN_TYPE)) {
                            throw new ServerException(401, I18nUtils.getMessage("error.token.expired"));
                        }
                        UserTokenVerifier verifier = userTokenVerifierProvider.getIfAvailable();
                        if (verifier == null || !verifier.isActive(userId, encryptedToken)) {
                            throw new ServerException(401, I18nUtils.getMessage("error.token.expired"));
                        }
                    }
                    if (principalType != null && !principalType.isEmpty()) {
                        payload.put("principalType", principalType);
                        payload.put("principalId", principalId);
                    }
                    payload.put("userId", userId);
                    copyContextHeader(request, payload, "X-Organization-Id", "organizationId");
                    copyContextHeader(request, payload, "X-Team-Id", "teamId");
                    if (tenantId != null && !tenantId.isEmpty()) payload.put("tenantId", tenantId);
                    payload.put("token", token);
                } catch (ServerException e) {
                    handleException(response, e);
                    return;
                } catch (Exception e) {
                    handleException(response, new ServerException(401, I18nUtils.getMessage("error.token.expired")));
                    return;
                }
            }

            // 设置请求开始时间
            payload.put("startTime", String.valueOf(startTime));
            payload.put("traceId", traceId);
            CurrentUser.set(payload);
            if (payload.get("tenantId") != null && !payload.get("tenantId").trim().isEmpty()) {
                MDC.put("tenantId", payload.get("tenantId"));
            }

            // 继续执行后续过滤器和控制器
            filterChain.doFilter(request, response);
        } catch (ServerException e) {
            handleException(response, e);
        } catch (Exception e) {
            log.error("请求处理异常，类型：{}，traceId：{}", e.getClass().getName(), traceId);
            handleException(response, new ServerException(500, I18nUtils.getMessage("system.internal.error")));
        } finally {
            // 记录请求日志
            try {
                long duration = System.currentTimeMillis() - startTime;
                String userId = payload.get("userId");
                log.info("用户:{}, 耗时:{}ms, {} {}, 状态:{}",
                        userId != null ? userId : "匿名",
                        duration,
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus());
                MeterRegistry registry = meterRegistryProvider.getIfAvailable();
                if (registry != null) {
                    registry.timer("aether.http.requests", "method", request.getMethod(),
                            "route", normalizeRoute(request.getRequestURI()),
                            "status", String.valueOf(response.getStatus())).record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
                }
            } catch (Exception e) {
                // 日志记录失败不影响主流程
                log.warn("请求日志记录失败，类型：{}", e.getClass().getName());
            }

            // 清理ThreadLocal，防止内存泄漏
            CurrentUser.remove();
            MDC.remove("traceId");
            MDC.remove("spanId");
            MDC.remove("tenantId");
        }
    }

    private void copyContextHeader(HttpServletRequest request, HashMap<String, String> payload,
                                   String header, String key) {
        String value = request.getHeader(header);
        if (value != null && value.matches("[A-Za-z0-9_-]{1,128}")) payload.put(key, value);
    }

    private String normalizeRoute(String uri) {
        if (uri == null || uri.trim().isEmpty()) return "unknown";
        return uri.replaceAll("/[0-9a-fA-F-]{16,}", "/{id}");
    }

    private String normalizeTraceId(String traceId) {
        String value = traceId == null ? "" : traceId.replaceAll("[^0-9a-fA-F]", "").toLowerCase();
        if (value.length() > 32) value = value.substring(0, 32);
        StringBuilder result = new StringBuilder(value);
        while (result.length() < 32) result.append('0');
        if (result.toString().matches("0{32}")) result.setCharAt(31, '1');
        return result.toString();
    }

    /**
     * 处理Exception。
     */
    private void handleException(HttpServletResponse response, ServerException e) throws IOException {
        // 令牌过期、权限不足属于预期的客户端请求，不打印完整堆栈，避免无效错误日志淹没真实故障。
        Integer statusCode = resolveStatusCode(e.getMessage());
        if (statusCode != null && statusCode >= 400 && statusCode < 500) {
            log.warn("请求鉴权失败，类型：{}，traceId：{}", e.getClass().getName(), MDC.get("traceId"));
        } else {
            log.error("过滤器异常，类型：{}，traceId：{}", e.getClass().getName(), MDC.get("traceId"));
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        String message = e.getMessage();
        WebResponse<String> webResponse;
        String[] parts = message != null ? message.split(":", 2) : new String[0];
        if (parts.length == 2) {
            try {
                int code = Integer.parseInt(parts[0].trim());
                webResponse = WebResponse.Error(code, sanitize(parts[1].trim()), null);
            } catch (NumberFormatException ex) {
                webResponse = WebResponse.Error(sanitize(message), null);
            }
        } else {
            webResponse = WebResponse.Error(sanitize(message), null);
        }
        response.getWriter().write(JSON.toJSONString(webResponse));
    }

    /**
     * 解析约定为“状态码:提示”的业务异常状态码。
     */
    private Integer resolveStatusCode(String message) {
        if (message == null) return null;
        String[] parts = message.split(":", 2);
        if (parts.length != 2) return null;
        try {
            return Integer.parseInt(parts[0].trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String sanitize(String message) {
        if (message == null) return null;
        return message.replaceAll("(?i)(password|passwd|secret|token|api[_-]?key)(\\s*[=:]\\s*)[^,;\\s]+", "$1$2[REDACTED]");
    }

    /**
     * 服务账号 token 只允许访问外部业务接入 API，不能进入后台管理面。
     */
    private boolean isServiceAccountPathAllowed(String uri) {
        if (uri == null) return false;
        return uri.startsWith("/api/business/") || uri.startsWith("/openapi/v1/") || "/api/auth/service-account/token".equals(uri);
    }
}
