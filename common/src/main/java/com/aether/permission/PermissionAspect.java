package com.aether.permission;

import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.utils.TokenUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.HashMap;

/** 基于平台角色资源的权限切面。 */
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
        Class<?> targetClass = AopUtils.getTargetClass(joinPoint.getTarget());
        Permission classAnnotation = AnnotationUtils.findAnnotation(targetClass, Permission.class);
        if (classAnnotation == null) {
            classAnnotation = AnnotationUtils.findAnnotation(joinPoint.getSignature().getDeclaringType(), Permission.class);
        }
        Permission methodAnnotation = AnnotationUtils.findAnnotation(method, Permission.class);
        if (methodAnnotation == null) {
            methodAnnotation = AnnotationUtils.findAnnotation(signature.getMethod(), Permission.class);
        }
        if (methodAnnotation == null) {
            for (Method declaredMethod : targetClass.getDeclaredMethods()) {
                if (declaredMethod.getName().equals(method.getName())
                        && declaredMethod.getParameterCount() == method.getParameterCount()) {
                    methodAnnotation = AnnotationUtils.findAnnotation(declaredMethod, Permission.class);
                    if (methodAnnotation != null) break;
                }
            }
        }
        Permission annotation = methodAnnotation != null ? methodAnnotation : classAnnotation;
        if (annotation == null || !annotation.required()) return;

        HashMap<String, String> user = CurrentUser.getUser();
        if (user == null || user.get("userId") == null) {
            throw new ServerException(401, I18nUtils.getMessage("auth.error.no.permission"));
        }
        HashOperations<String, Object, Object> operations = redisTemplate.opsForHash();
        Object permissionMap = operations.get(TokenUtils.TOKEN_KEY, user.get("userId"));
        if (permissionMap instanceof HashMap) {
            HashMap<String, Object> map = (HashMap<String, Object>) permissionMap;
            Boolean permission = (Boolean) map.get(annotation.path());
            if (permission != null && annotation.type() == Permission.Type.Read) return;
            if (permission != null && annotation.type() == Permission.Type.Write && permission) return;
        }
        throw new ServerException(403, I18nUtils.getMessage("auth.error.no.permission"));
    }
}
