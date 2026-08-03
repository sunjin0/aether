package com.aether.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MyLocaleResolverTest {
    private final MyLocaleResolver localeResolver = new MyLocaleResolver();

    @Test
    void resolvesSupportedLanguageRanges() {
        assertEquals(Locale.US, resolve("en-US,en;q=0.9"));
        assertEquals(Locale.US, resolve("en"));
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolve("zh-CN,zh;q=0.9"));
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolve("zh"));
    }

    @Test
    void fallsBackToChineseForMissingOrUnsupportedLanguage() {
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolve(null));
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolve("fr-FR,fr;q=0.9"));
        assertEquals(Locale.SIMPLIFIED_CHINESE, resolve("invalid language header"));
    }

    private Locale resolve(String acceptLanguage) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (acceptLanguage != null) {
            request.addHeader("Accept-Language", acceptLanguage);
        }
        return localeResolver.resolveLocale(request);
    }
}
