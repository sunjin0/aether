package com.aether.validator;


import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 校验实体
 *
 * @author sun
 * @since 2024/09/23
 */
@Constraint(validatedBy = GenericValidator.class)
@Target({ElementType.PARAMETER, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEntity {

    String message() default "";


    /**
     * 所有字段是否必须校验
     *
     * @return boolean
     */
    boolean all() default false;

    /**
     * 字段规则数组
     *
     * @return FieldRule[]
     */
    FieldRule[] fields() default {};

    /**
     * 字段名称
     *
     * @return String[]
     */
    String[] fieldNames() default {};

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};


}





