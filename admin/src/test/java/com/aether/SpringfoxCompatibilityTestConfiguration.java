package com.aether;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.List;

/** Springfox 2.x 与 Spring MVC 5.3 的测试兼容处理。 */
@TestConfiguration
public class SpringfoxCompatibilityTestConfiguration {
    @Bean
    public static BeanPostProcessor springfoxHandlerMappingsFilter() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                String className = bean.getClass().getName();
                if (!className.contains("WebMvcRequestHandlerProvider")) {
                    return bean;
                }
                Field field = ReflectionUtils.findField(bean.getClass(), "handlerMappings");
                if (field == null) {
                    return bean;
                }
                ReflectionUtils.makeAccessible(field);
                Object value = ReflectionUtils.getField(field, bean);
                if (value instanceof List) {
                    ((List<?>) value).removeIf(mapping -> {
                        try {
                            return mapping.getClass().getMethod("getPatternParser").invoke(mapping) != null;
                        } catch (Exception ignored) {
                            return false;
                        }
                    });
                }
                return bean;
            }
        };
    }
}
