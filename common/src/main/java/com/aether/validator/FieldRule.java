package com.aether.validator;

/**
 * 字段规则
 *
 * @author sun
 * @since 2024/09/23
 */
public @interface FieldRule {
    // 字段名称
    String field();

    // 提示信息
    String message() default "";

    // 最小长度
    int min() default -1;

    // 最大长度
    int max() default -1;

    // 类型, 默认为字符串
    Type type() default Type.STRING;


}


