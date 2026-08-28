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
        return execute(scope, key, null, type, action);
    }

    /**
     * A key identifies one canonical request. Reusing it for another payload is
     * an integration error, never a request to return a stale response.
     */
    public <T> T execute(String scope, String key, String fingerprint, Class<T> type, Supplier<T> action) {
        if (key == null || key.trim().isEmpty()) throw new ServerException(422, "Idempotency-Key 不能为空");
        String redisKey = PREFIX + scope + ":" + key;
        Object existing = redisTemplate.opsForValue().get(redisKey);
        if (existing != null) {
            if (PENDING.equals(String.valueOf(existing))) throw new ServerException(409, "相同幂等键的请求正在执行");
            com.alibaba.fastjson2.JSONObject cached = JSON.parseObject(String.valueOf(existing));
            if (cached.containsKey("response")) {
                if (fingerprint != null && !fingerprint.equals(cached.getString("fingerprint")))
                    throw new ServerException(409, "IDEMPOTENCY_KEY_REUSED");
                return JSON.parseObject(cached.getString("response"), type);
            }
            // Compatibility with cache values written before fingerprints were introduced.
            if (fingerprint != null) throw new ServerException(409, "IDEMPOTENCY_KEY_REUSED");
            return JSON.parseObject(String.valueOf(existing), type);
        }
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(redisKey, PENDING, 10, TimeUnit.MINUTES);
        if (!Boolean.TRUE.equals(locked)) return execute(scope, key, type, action);
        try {
            T result = action.get();
            com.alibaba.fastjson2.JSONObject stored = new com.alibaba.fastjson2.JSONObject();
            stored.put("fingerprint", fingerprint);
            stored.put("response", JSON.toJSONString(result));
            redisTemplate.opsForValue().set(redisKey, stored.toJSONString(), 24, TimeUnit.HOURS);
            return result;
        } catch (RuntimeException ex) {
            redisTemplate.delete(redisKey);
            throw ex;
        }
    }
}
