package com.aether.interceptor;

import com.aether.local.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Redis fixed-window request limiter. A limit of zero disables the filter. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private final StringRedisTemplate redis;
    private final long limit;
    private final long windowSeconds;
    private final boolean failOpen;

    public RateLimitFilter(StringRedisTemplate redis,
                           @Value("${aether.reliability.rate-limit.requests-per-window:0}") long limit,
                           @Value("${aether.reliability.rate-limit.window-seconds:60}") long windowSeconds,
                           @Value("${aether.reliability.rate-limit.fail-open:true}") boolean failOpen) {
        this.redis = redis;
        this.limit = limit;
        this.windowSeconds = Math.max(1L, windowSeconds);
        this.failOpen = failOpen;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (limit <= 0 || isExcluded(request)) {
            chain.doFilter(request, response);
            return;
        }
        String tenant = CurrentUser.getUser() == null ? "anonymous" : CurrentUser.getUser().get("tenantId");
        if (tenant == null || tenant.trim().isEmpty()) tenant = "public";
        String client = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        String bucket = String.valueOf(System.currentTimeMillis() / (windowSeconds * 1000L));
        String key = "aether:rate:" + safe(tenant) + ":" + safe(client) + ":" + safe(request.getRequestURI()) + ":" + bucket;
        Long count;
        try {
            count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) redis.expire(key, windowSeconds, TimeUnit.SECONDS);
        } catch (RuntimeException ex) {
            log.warn("Rate limiter backend unavailable, failOpen={}", failOpen);
            if (failOpen) {
                chain.doFilter(request, response);
                return;
            }
            response.setStatus(503);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":503,\"message\":\"限流服务暂不可用\"}");
            return;
        }
        if (count != null && count > limit) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(windowSeconds));
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isExcluded(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && (uri.startsWith("/actuator/health") || uri.startsWith("/actuator/info"));
    }

    private String safe(String value) {
        return value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9._/-]", "_");
    }
}
