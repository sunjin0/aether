package com.aether.config;

import org.springframework.web.servlet.LocaleResolver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 国际化解析器
 *
 * @author sun
 * @since 2024/09/02
 */
public class MyLocaleResolver implements LocaleResolver {
    private static final Locale DEFAULT_LOCALE = Locale.SIMPLIFIED_CHINESE;
    private static final List<Locale> SUPPORTED_LOCALES = Arrays.asList(
            Locale.SIMPLIFIED_CHINESE, Locale.US);

    @Override
    public Locale resolveLocale(HttpServletRequest httpServletRequest) {
        String language = httpServletRequest.getHeader("Accept-Language");
        if (language == null || language.trim().isEmpty()) {
            return DEFAULT_LOCALE;
        }

        try {
            for (Locale.LanguageRange range : Locale.LanguageRange.parse(language)) {
                if (range.getWeight() <= 0D || "*".equals(range.getRange())) {
                    continue;
                }
                Locale requested = Locale.forLanguageTag(range.getRange());
                for (Locale supported : SUPPORTED_LOCALES) {
                    if (supported.getLanguage().equalsIgnoreCase(requested.getLanguage())) {
                        return supported;
                    }
                }
            }
            return DEFAULT_LOCALE;
        } catch (IllegalArgumentException ignored) {
            return DEFAULT_LOCALE;
        }
    }

    @Override
    public void setLocale(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Locale locale) {

    }
}
