package com.aether.i18n;

import java.util.Locale;

public interface I18nService {
    String getMessage(String code);

    String getMessage(String code, Object[] args);

    String getMessage(String code, Object[] args, String defaultMessage);

    String getMessage(String code, Object[] args, String defaultMessage, Locale locale);
}
