package com.aether.i18n;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 表示I18nUtils。
 */
@Component
public class I18nUtils {

    private static I18nService i18nService;

    /**
     * 创建 {@code I18nUtils} 实例。
     */
    @Autowired
    public I18nUtils(@Qualifier("i18nServiceImpl") I18nService i18nService) {
        I18nUtils.i18nService = i18nService;
    }

    /**
     * 获取消息。
     */
    public static String getMessage(String code) {
        return i18nService.getMessage(code);
    }

    /**
     * 获取消息。
     */
    public static String getMessage(String code, Object[] args) {
        return i18nService.getMessage(code, args);
    }

    /**
     * 获取消息。
     */
    public static String getMessage(String code, Object[] args, String defaultMessage) {
        return i18nService.getMessage(code, args, defaultMessage);
    }

    /**
     * 获取消息。
     */
    public static String getMessage(String code, Locale locale) {
        return i18nService.getMessage(code, null, null, locale);
    }

}
