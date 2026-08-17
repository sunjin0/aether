package com.aether.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证MyLocaleResolver的行为。
 */
class MyLocaleResolverTest {
    private final MyLocaleResolver localeResolver = new MyLocaleResolver();

    /**
     * 处理resolvesSupportedLanguageRanges。
     */
    @Test
    void resolvesSupportedLanguageRanges() {
        assertEquals(Locale.US, resolve("en-US,en;q=0.9"));
        assertEquals(Locale.US, resolve("en"));
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolve("zh-CN,zh;q=0.9"));
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolve("zh"));
    }

    /**
     * 处理fallsBackToChinese用于MissingOrUnsupportedLanguage。
     */
    @Test
    void fallsBackToChineseForMissingOrUnsupportedLanguage() {
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolve(null));
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolve("fr-FR,fr;q=0.9"));
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolve("invalid language header"));
    }

    /**
     * 解析当前请求。
     */
    private Locale resolve(String acceptLanguage) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (acceptLanguage != null) {
            request.addHeader("Accept-Language", acceptLanguage);
        }
        return localeResolver.resolveLocale(request);
    }
}
