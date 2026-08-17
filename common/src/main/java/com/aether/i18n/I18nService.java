package com.aether.i18n;

import java.util.Locale;

/**
 * 定义I18n业务服务契约。
 */
public interface I18nService {
    /**
     * 获取消息。
     */
    String getMessage(String code);

    /**
     * 获取消息。
     */
    String getMessage(String code, Object[] args);

    /**
     * 获取消息。
     */
    String getMessage(String code, Object[] args, String defaultMessage);

    /**
     * 获取消息。
     */
    String getMessage(String code, Object[] args, String defaultMessage, Locale locale);
}
