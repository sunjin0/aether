package com.aether.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.servlet.LocaleResolver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Locale;

/**
 * 国际化解析器
 *
 * @author sun
 * @since 2024/09/02
 */
public class MyLocaleResolver implements LocaleResolver {
    //解析请求
    @Override
    public Locale resolveLocale(HttpServletRequest httpServletRequest) {
        //获取请求中的语言参数
        String language = httpServletRequest.getHeader("Accept-Language");
        //默认的；如果没有就使用默认的
        Locale locale = Locale.getDefault();
        //如果请求的了携带了国际化的参数
        if (!StringUtils.isEmpty(language)) {
            String[] strings = language.split(",");
            String string = strings[0].replace("-", "_");
            //zh_CN
            String[] split = string.split("_");
            //国家、地区
            locale = new Locale(split[0], split[1]);
        }
        return locale;
    }

    @Override
    public void setLocale(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Locale locale) {

    }
}
