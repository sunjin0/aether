package com.aether.permission;

import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.auth.PermissionCache;
import com.aether.auth.UserPermissionProvider;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** 基于平台角色资源的权限切面。 */
@Component
@Aspect
public class PermissionAspect {
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private org.springframework.beans.factory.ObjectProvider<UserPermissionProvider> permissionProvider;

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
        Map<String, Object> map = null;
        try {
            map = PermissionCache.get(redisTemplate, user.get("userId"));
        } catch (Exception ignored) {
            // Redis 短暂不可用时仍尝试通过业务持久化层重建快照，最终按权限失败关闭。
        }
        if (!PermissionCache.isComplete(map) && permissionProvider != null) {
            UserPermissionProvider provider = permissionProvider.getIfAvailable();
            if (provider != null) {
                try {
                    map = provider.loadPermissionMap(user.get("userId"), user.get("encryptedToken"));
                    PermissionCache.put(redisTemplate, user.get("userId"), map);
                } catch (Exception ignored) {
                    // 下面统一返回 403，避免异常细节泄漏给客户端。
                }
            }
        }
        if (map != null) {
            Object value = map.get(annotation.path());
            boolean permission = value instanceof Boolean
                    ? (Boolean) value
                    : Boolean.parseBoolean(String.valueOf(value));
            if (value != null && annotation.type() == Permission.Type.Read) return;
            if (value != null && annotation.type() == Permission.Type.Write && permission) return;
        }
        throw new ServerException(403, I18nUtils.getMessage("auth.error.no.permission"));
    }
}
