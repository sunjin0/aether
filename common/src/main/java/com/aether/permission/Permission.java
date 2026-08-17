package com.aether.permission;

import java.lang.annotation.*;

/**
 * 表示权限。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Permission {

    /**
     * 权限路径，默认为方法路径
     * path对应值在资源表中的path字段
     *
     * @return {@link String }
     */
    String path() default "";

    /**
     * 是否验证权限
     *
     * @return boolean
     */
    boolean required() default true;

    /**
     * 接口类型
     *
     * @return {@link String }
     */
    Type type() default Type.Read;

    /**
     * 表示Type。
     */
    enum Type {
        Read, Write
    }
}
