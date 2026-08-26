package com.aether.openapi.service;

import com.alibaba.fastjson2.JSON;
import com.aether.exception.ServerException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** 外部写请求幂等：仅缓存安全响应，不缓存异常或敏感执行上下文。 */
@Service
public class OpenApiIdempotencyService {
    private static final String PREFIX = "OpenApiIdempotency:";
    private static final String PENDING = "__PENDING__";
    private final RedisTemplate<String, Object> redisTemplate;
    public OpenApiIdempotencyService(RedisTemplate<String, Object> redisTemplate) { this.redisTemplate = redisTemplate; }
    public <T> T execute(String scope, String key, Class<T> type, Supplier<T> action) {
        if (key == null || key.trim().isEmpty()) throw new ServerException(422, "Idempotency-Key 不能为空");
        String redisKey = PREFIX + scope + ":" + key;
        Object existing = redisTemplate.opsForValue().get(redisKey);
        if (existing != null) {
            if (PENDING.equals(String.valueOf(existing))) throw new ServerException(409, "相同幂等键的请求正在执行");
            return JSON.parseObject(String.valueOf(existing), type);
        }
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(redisKey, PENDING, 10, TimeUnit.MINUTES);
        if (!Boolean.TRUE.equals(locked)) return execute(scope, key, type, action);
        try {
            T result = action.get();
            redisTemplate.opsForValue().set(redisKey, JSON.toJSONString(result), 24, TimeUnit.HOURS);
            return result;
        } catch (RuntimeException ex) {
            redisTemplate.delete(redisKey);
            throw ex;
        }
    }
}
