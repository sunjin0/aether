package com.aether.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 实现I18n业务服务。
 */
@Service
public class I18nServiceImpl implements I18nService {

    private final MessageSource messageSource;

    /**
     * 创建 {@code I18nServiceImpl} 实例。
     */
    public I18nServiceImpl(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * 获取消息。
     */
    @Override
    public String getMessage(String code) {
        if (messageSource != null) {
            Locale locale = LocaleContextHolder.getLocale();
            return messageSource.getMessage(code, null, locale);
        }
        return null;
    }

    /**
     * 获取消息。
     */
    @Override
    public String getMessage(String code, Object[] args) {
        if (messageSource != null) {
            Locale locale = LocaleContextHolder.getLocale();
            return messageSource.getMessage(code, args, locale);
        }
        return null;
    }

    /**
     * 获取消息。
     */
    @Override
    public String getMessage(String code, Object[] args, String defaultMessage) {
        if (messageSource != null) {
            Locale locale = LocaleContextHolder.getLocale();
            return messageSource.getMessage(code, args, defaultMessage, locale);
        }
        return null;
    }

    /**
     * 获取消息。
     */
    @Override
    public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
        if (messageSource != null) {
            return messageSource.getMessage(code, args, defaultMessage, locale);
        }
        return null;
    }
}
