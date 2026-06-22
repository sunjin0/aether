package com.aether.permission;

import com.aether.exception.ServerException;
import  com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.utils.TokenUtils;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import javax.annotation.Resource;
import java.lang.reflect.Method;

/**
 * AOP 权限
 *
 * @author sun
 * @since 2024/11/26
 */
@Component
@Aspect
public class PermissionAspect {
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Pointcut("@within(com.aether.permission.Permission) || @annotation(com.aether.permission.Permission)")
    public void pointcut() {

    }

    @Before("pointcut()")
    public void before(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 获取类上的注解
        Permission classAnnotation = (Permission) joinPoint.getSignature().getDeclaringType().getAnnotation(Permission.class);
        // 获取方法上的注解
        Permission methodAnnotation = method.getAnnotation(Permission.class);

        // 优先使用方法上的注解，如果没有则使用类上的注解
        Permission annotation = methodAnnotation != null ? methodAnnotation : classAnnotation;
        if (annotation == null) {
            return;
        }
        //获取注解参数
        String path = annotation.path();
        boolean required = annotation.required();
        Permission.Type type = annotation.type();
        if (!required)
            return;

        HashMap<String, String> user = CurrentUser.getUser();
        if (user == null|| user.get("userId") == null) {
            throw new ServerException(401, I18nUtils.getMessage("auth.error.no.permission"));
        }
        String userId = user.get("userId");
        HashOperations<String, Object, Object> operations = redisTemplate.opsForHash();

        Object permissionMap = operations.get(TokenUtils.TOKEN_KEY, userId);
        if (permissionMap instanceof HashMap) {
            HashMap<String, Object> map = (HashMap<String, Object>) permissionMap;
            Boolean permission = (Boolean) map.get(path);
            // 判断是否是读操作
            if (permission != null && type == Permission.Type.Read) {
                return;
            }
            // 判断是否是写操作
            if (permission != null && type == Permission.Type.Write && permission) {
                return;
            }
        }

        throw new ServerException(403, I18nUtils.getMessage("auth.error.no.permission"));
    }
}
