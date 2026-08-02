package com.aether.interceptor;

import com.alibaba.fastjson2.JSON;
import com.aether.entity.WebResponse;
import com.aether.auth.ServiceTokenVerifier;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.utils.AesUtil;
import com.aether.utils.TokenUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.beans.factory.ObjectProvider;
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

/**
 * 全局过滤器（替代GlobalInterceptor）
 *
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
    private final ObjectProvider<ServiceTokenVerifier> serviceTokenVerifierProvider;

    public GlobalFilter(ObjectProvider<ServiceTokenVerifier> serviceTokenVerifierProvider) {
        this.serviceTokenVerifierProvider = serviceTokenVerifierProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        HashMap<String, String> payload = new HashMap<>();

        try {
            // 认证处理
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                try {
                    String token = AesUtil.decrypt(authorization.replace("Bearer ", ""));
                    // 校验token（签名+过期时间）
                    String userId = TokenUtils.getUserId(token);
                    String serviceAccountId = TokenUtils.getClaim(token, "serviceAccountId");
                    if (serviceAccountId != null && !serviceAccountId.isEmpty()) {
                        ServiceTokenVerifier verifier = serviceTokenVerifierProvider.getIfAvailable();
                        String tokenVersion = TokenUtils.getClaim(token, "serviceTokenVersion");
                        if (verifier == null || !verifier.isActive(serviceAccountId, tokenVersion)) {
                            throw new ServerException(401, "服务账号令牌已失效");
                        }
                        payload.put("serviceAccountId", serviceAccountId);
                    }
                    payload.put("userId", userId);
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
            CurrentUser.set(payload);

            // 继续执行后续过滤器和控制器
            filterChain.doFilter(request, response);
        } catch (ServerException e) {
            handleException(response, e);
        } catch (Exception e) {
            log.error("请求处理异常", e);
            handleException(response, new ServerException(500, e.getMessage()));
        } finally {
            // 记录请求日志
            try {
                long duration = System.currentTimeMillis() - startTime;
                String userId = payload.get("userId");
                String query = request.getQueryString();
                log.info("用户:{}, 耗时:{}ms, {} {}, 状态:{}",
                        userId != null ? userId : "匿名",
                        duration,
                        request.getMethod(),
                        query != null ? request.getRequestURI() + "?" + query : request.getRequestURI(),
                        response.getStatus());
            } catch (Exception e) {
                // 日志记录失败不影响主流程
                log.warn("请求日志记录失败", e);
            }

            // 清理ThreadLocal，防止内存泄漏
            CurrentUser.remove();
        }
    }

    private void handleException(HttpServletResponse response, ServerException e) throws IOException {
        log.error("过滤器异常：", e);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        String message = e.getMessage();
        WebResponse<String> webResponse;
        String[] parts = message != null ? message.split(":", 2) : new String[0];
        if (parts.length == 2) {
            try {
                int code = Integer.parseInt(parts[0].trim());
                webResponse = WebResponse.Error(code, parts[1].trim(), null);
            } catch (NumberFormatException ex) {
                webResponse = WebResponse.Error(message, null);
            }
        } else {
            webResponse = WebResponse.Error(message, null);
        }
        response.getWriter().write(JSON.toJSONString(webResponse));
    }
}
