package com.aether.sys.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Carves out only anonymous authentication endpoints from Spring Security's
 * default HTTP Basic challenge when SAML is not enabled. All other requests
 * continue through the existing security chain.
 */
@Configuration
@ConditionalOnProperty(prefix = "aether.identity.saml", name = "enabled", havingValue = "false", matchIfMissing = true)
public class ApiSecurityConfiguration {
    @Bean
    @Order(1)
    public SecurityFilterChain anonymousAuthenticationEndpoints(HttpSecurity http) throws Exception {
        RequestMatcher authenticationEndpoints = new OrRequestMatcher(
                new AntPathRequestMatcher("/api/sys/verify"),
                new AntPathRequestMatcher("/api/sys/login"),
                new AntPathRequestMatcher("/api/sys/send"),
                new AntPathRequestMatcher("/api/sys/refresh"));
        http.requestMatcher(authenticationEndpoints)
                .authorizeRequests().anyRequest().permitAll()
                .and()
                .csrf().disable()
                .httpBasic().disable()
                .formLogin().disable();
        return http.build();
    }
}
