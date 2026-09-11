package com.aether.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 表示Springfox兼容配置。
 *
 * <p>Springfox 2.10.5 与 Spring Boot 2.7 不兼容：Spring MVC 5.3 会注册基于
 * {@code PathPatternRequestCondition} 的 handler mapping，而 Springfox 的
 * {@code WebMvcRequestHandlerProvider} 只认 {@code PatternsRequestCondition}，
 * 在 {@code DocumentationPluginsBootstrapper} 启动时对前者调用 {@code getPatterns()} 抛
 * {@code NullPointerException}，导致上下文取消、进程退出。</p>
 *
 * <p>处置办法是把 Springfox 视图里带 pattern parser 的 handler mapping 剔除掉——它们只影响
 * 接口文档的路径匹配，不影响业务路由。{@code spring.mvc.pathmatch.matching-strategy=ant_path_matcher}
 * 治不了这个问题（实测：写成命令行参数强制指定同样崩溃），因为资源与 Actuator 的映射仍走 PathPattern。</p>
 *
 * <p>仅当 {@code knife4j.enable=true} 时注册：那是把整套 Springfox 机制拉起来的开关，prod profile
 * 下它为 {@code false}，本配置不生效、生产行为与改动前完全一致。</p>
 */
@Configuration
@ConditionalOnProperty(name = "knife4j.enable", havingValue = "true")
public class SpringfoxCompatibilityConfig {

    /**
     * 过滤 Springfox 处理器映射。
     *
     * <p>声明为 static 以避免配置类被过早实例化。剔除动作发生在 bean 初始化之后，此时
     * {@code WebMvcRequestHandlerProvider} 的 {@code handlerMappings} 已装配完成。</p>
     */
    @Bean
    public static BeanPostProcessor springfoxHandlerMappingsFilter() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!bean.getClass().getName().contains("WebMvcRequestHandlerProvider")) {
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
