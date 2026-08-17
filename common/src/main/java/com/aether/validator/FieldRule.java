package com.aether.validator;

/**
 * 字段规则
 *
 * @author sun
 * @since 2024/09/23
 */
public @interface FieldRule {
    // 字段名称

    /**
     * 处理field。
     */
    String field();

    // 提示信息

    /**
     * 消息当前请求。
     */
    String message() default "";

    // 最小长度

    /**
     * 处理min。
     */
    int min() default -1;

    // 最大长度

    /**
     * 处理max。
     */
    int max() default -1;

    // 类型, 默认为字符串

    /**
     * 处理type。
     */
    Type type() default Type.STRING;


}


