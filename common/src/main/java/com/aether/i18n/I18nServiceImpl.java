package com.aether.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class I18nServiceImpl implements I18nService {

    private final MessageSource messageSource;

    public I18nServiceImpl(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    public String getMessage(String code) {
        if (messageSource != null) {
            Locale locale = LocaleContextHolder.getLocale();
            return messageSource.getMessage(code, null, locale);
        }
        return null;
    }

    @Override
    public String getMessage(String code, Object[] args) {
        if (messageSource != null) {
            Locale locale = LocaleContextHolder.getLocale();
            return messageSource.getMessage(code, args, locale);
        }
        return null;
    }

    @Override
    public String getMessage(String code, Object[] args, String defaultMessage) {
        if (messageSource != null) {
            Locale locale = LocaleContextHolder.getLocale();
            return messageSource.getMessage(code, args, defaultMessage, locale);
        }
        return null;
    }

    @Override
    public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
        if (messageSource != null) {
            return messageSource.getMessage(code, args, defaultMessage, locale);
        }
        return null;
    }
}
