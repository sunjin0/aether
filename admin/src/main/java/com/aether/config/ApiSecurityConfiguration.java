package com.aether.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * API uses the application's bearer-token filter and permission interceptor.
 * Spring Security must not install its browser-oriented login/basic-auth entry points,
 * which otherwise turn unauthenticated API calls into 302 redirects or 401 Basic responses.
 */
@Configuration
@EnableWebSecurity
public class ApiSecurityConfiguration {
    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .httpBasic().disable()
                .formLogin().disable()
                .logout().disable()
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }
}
