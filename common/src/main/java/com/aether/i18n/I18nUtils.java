package com.aether.i18n;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class I18nUtils {

    private static I18nService i18nService;

    @Autowired
    public I18nUtils(@Qualifier("i18nServiceImpl") I18nService i18nService) {
        I18nUtils.i18nService = i18nService;
    }

    public static String getMessage(String code) {
        return i18nService.getMessage(code);
    }

    public static String getMessage(String code, Object[] args) {
        return i18nService.getMessage(code, args);
    }

    public static String getMessage(String code, Object[] args, String defaultMessage) {
        return i18nService.getMessage(code, args, defaultMessage);
    }

    public static String getMessage(String code, Locale locale) {
        return i18nService.getMessage(code, null, null, locale);
    }

}
