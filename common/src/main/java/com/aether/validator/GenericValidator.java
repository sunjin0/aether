package com.aether.validator;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.lang.reflect.Field;
import java.util.Map;

@Component
public class GenericValidator implements ConstraintValidator<ValidEntity, Object> {

    private static final Logger log = LoggerFactory.getLogger(GenericValidator.class);

    private FieldRule[] fieldRules;

    private String[] fieldNames;

    private boolean all = false;

    /**
     * 是有效字符串
     *
     * @param context 上下文
     * @param isValid 有效
     * @param entity  实体
     * @param value   价值
     * @param type    类型
     * @return boolean
     */
    private static boolean isValidString(ConstraintValidatorContext context, boolean isValid, FieldRule entity, Object value, Type type) {
        if (value instanceof String || value instanceof Number) {
            String strValue = (value + "").trim();
            // 处理邮箱
            boolean email = (type == Type.EMAIL) && !value.toString().matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
            isValid = BuildError(email, context, entity.field() + " is not a valid email address.", entity, isValid);
            // 处理手机号
            boolean phone = (type == Type.PHONE) && !value.toString().matches("^\\d{11}$");
            isValid = BuildError(phone, context, entity.field() + " is not a valid phone number.", entity, isValid);
            // 处理最小长度
            boolean min = entity.min() >= 0 && strValue.length() < entity.min();
            isValid = BuildError(min, context, entity.field() + " must be at least " + entity.min() + " characters long.", entity, isValid);
            // 处理最大长度
            boolean max = entity.max() >= 0 && strValue.length() > entity.max();
            isValid = BuildError(max, context, entity.field() + " must be no more than " + entity.max() + " characters long.", entity, isValid);
            //处理数值范围
            boolean range = entity.min() >= 0 && entity.max() >= 0 && strValue.length() > entity.min() && strValue.length() > entity.max();
            isValid = BuildError(range, context, entity.field() + " must be between " + entity.min() + " and " + entity.max() + " characters long.", entity, isValid);

        }
        return isValid;
    }

    /**
     * 构建错误<br>
     * 如果isValid为true,则构建错误信息
     *
     * @param isValidated 经过验证
     * @param context     上下文
     * @param message     消息
     * @param entity      实体
     * @param isValid     有效
     * @return boolean
     */
    private static boolean BuildError(boolean isValidated, ConstraintValidatorContext context, String message, FieldRule entity, boolean isValid) {
        if (isValidated) {
            context.buildConstraintViolationWithTemplate(message)
                    .addPropertyNode(entity.field())
                    .addConstraintViolation();
            isValid = false;
        }
        return isValid;
    }

    /**
     * 打印错误
     *
     * @param fieldName 字段名称
     */
    private static void PrintError(String fieldName) {
        log.error("Field not found: {}", fieldName);
    }

    @Override
    public void initialize(ValidEntity constraintAnnotation) {
        fieldRules = constraintAnnotation.fields();
        fieldNames = constraintAnnotation.fieldNames();
        all = constraintAnnotation.all();
    }

    @Override
    public boolean isValid(Object obj, ConstraintValidatorContext context) {
        if (obj == null) return false;
        boolean isValid = true;
        // 处理当前类和父类的字段
        if (all) {
            Field[] fields = obj.getClass().getDeclaredFields();
            Field[] superFields = obj.getClass().getSuperclass().getDeclaredFields();
            fields = ArrayUtils.addAll(fields, superFields);
            for (Field field : fields) {
                field.setAccessible(true);
                Object value;
                try {
                    value = field.get(obj);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                if (value == null || value.toString().isEmpty()) {
                    isValid = false;
                }
            }
        } else {
            // 处理字段名
            isValid = isValid(obj, context, isValid);

            // 处理指定字段的自定义校验
            isValid = CustomCheck(obj, context, isValid);
        }
        return isValid;
    }

    /**
     * 自定义检查
     *
     * @param obj     对象
     * @param context 上下文
     * @param isValid 有效
     * @return boolean
     */
    private boolean CustomCheck(Object obj, ConstraintValidatorContext context, boolean isValid) {
        for (FieldRule entity : fieldRules) {
            try {
                Field field = null;
                Class<?> clazz = obj.getClass();
                while (clazz != null) {
                    try {
                        field = clazz.getDeclaredField(entity.field().trim());
                        break; // 找到字段后退出循环
                    } catch (NoSuchFieldException e) {
                        clazz = clazz.getSuperclass(); // 向上查找父类
                    }
                }
                if (field == null) {
                    PrintError(entity.field());
                    isValid = false;
                    continue;
                }
                field.setAccessible(true);
                // 获取值
                Object value = field.get(obj);
                //类型
                Type type = entity.type();
                // 处理非空
                isValid = BuildError((value == null || (value instanceof String && ((String) value).isEmpty())), context, entity.message().isEmpty()
                        ? entity.field() + " cannot be empty." : entity.message(), entity, isValid);
                // 处理字符串,数值长度
                isValid = isValidString(context, isValid, entity, value, type);
                // 处理数组长度
                isValid = isValidArray(context, isValid, entity, value, type);
                //处理hashMap
                isValid = isValidMap(context, isValid, entity, value, type);

            } catch (IllegalAccessException e) {
                e.printStackTrace();
                PrintError(entity.field());
                isValid = false;
            }
        }
        return isValid;
    }

    /**
     * 是有效map
     *
     * @param context 上下文
     * @param isValid 有效
     * @param entity  实体
     * @param value   价值
     * @param type    类型
     * @return boolean
     */
    private boolean isValidMap(ConstraintValidatorContext context, boolean isValid, FieldRule entity, Object value, Type type) {
        if (value instanceof Map) {
            Map<Object, Object> mapValue = (Map<Object, Object>) value;
            // 处理最小长度
            boolean min = (type == Type.MAP) && entity.min() >= 0 && mapValue.size() < entity.min();
            isValid = BuildError(min, context, entity.field() + " must have at least " + entity.min() + " elements.", entity, isValid);
            // 处理最大长度
            boolean max = (type == Type.MAP) && entity.max() >= 0 && mapValue.size() > entity.max();
            isValid = BuildError(max, context, entity.field() + " must have no more than " + entity.max() + " elements.", entity, isValid);
        }
        return isValid;
    }

    /**
     * 是有效数组
     *
     * @param context 上下文
     * @param isValid 有效
     * @param entity  实体
     * @param value   价值
     * @param type    类型
     * @return boolean
     */
    private boolean isValidArray(ConstraintValidatorContext context, boolean isValid, FieldRule entity, Object value, Type type) {
        if (value instanceof Object[]) {
            Object[] arrayValue = (Object[]) value;
            // 处理最小长度
            boolean min = (type == Type.ARRAY) && entity.min() >= 0 && arrayValue.length < entity.min();
            isValid = BuildError(min, context, entity.field() + " must have at least " + entity.min() + " elements.", entity, isValid);
            // 处理最大长度
            boolean max = (type == Type.ARRAY) && entity.max() >= 0 && arrayValue.length > entity.max();
            isValid = BuildError(max, context, entity.field() + " must have no more than " + entity.max() + " elements.", entity, isValid);
        }
        return isValid;
    }

    /**
     * 校验字段名是否为空
     *
     * @param obj     对象
     * @param context 上下文
     * @param isValid 有效
     * @return boolean
     */
    private boolean isValid(Object obj, ConstraintValidatorContext context, boolean isValid) {
        for (String fieldName : fieldNames) {
            try {
                Field field = null;
                Class<?> clazz = obj.getClass();
                while (clazz != null) {
                    try {
                        field = clazz.getDeclaredField(fieldName);
                        break; // 找到字段后退出循环
                    } catch (NoSuchFieldException e) {
                        clazz = clazz.getSuperclass(); // 向上查找父类
                    }
                }
                if (field == null) {
                    PrintError(fieldName);
                    isValid = false;
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(obj);
                // 处理非空
                if (value == null || (value instanceof String && ((String) value).isEmpty())) {
                    context
                            .buildConstraintViolationWithTemplate(fieldName + " cannot be empty")
                            .addPropertyNode(fieldName)
                            .addConstraintViolation();
                    isValid = false;
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                PrintError(fieldName);
                isValid = false;
            }
        }
        return isValid;
    }

}
