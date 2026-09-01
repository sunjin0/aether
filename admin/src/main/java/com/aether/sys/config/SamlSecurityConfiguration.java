package com.aether.sys.config;

import com.aether.entity.WebResponse;
import com.aether.sys.entity.OidcIdentityBinding;
import com.aether.sys.service.SamlIdentityMapper;
import com.aether.sys.service.UserService;
import com.aether.sys.vo.UserVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Enables SAML login only with a one-time tenant-bound RelayState. */
@Configuration
@ConditionalOnProperty(prefix = "aether.identity.saml", name = "enabled", havingValue = "true")
public class SamlSecurityConfiguration {
    private static final DefaultRedisScript<String> CONSUME_STATE = new DefaultRedisScript<String>(
            "local value = redis.call('get', KEYS[1]); "
                    + "if value then redis.call('del', KEYS[1]); end; return value", String.class);
    @Bean
    public SecurityFilterChain samlSecurityFilterChain(HttpSecurity http,
                                                       SamlIdentityMapper identityMapper,
                                                       UserService userService,
                                                       ObjectMapper objectMapper,
                                                       StringRedisTemplate redis) throws Exception {
        http.authorizeRequests().anyRequest().permitAll()
                .and().csrf().ignoringAntMatchers("/login/saml2/sso/**")
                .and().saml2Login(login -> login.successHandler(successHandler(identityMapper, userService, objectMapper, redis)));
        return http.build();
    }

    private AuthenticationSuccessHandler successHandler(SamlIdentityMapper mapper, UserService userService,
                                                         ObjectMapper objectMapper, StringRedisTemplate redis) {
        return (request, response, authentication) -> {
            String state = request.getParameter("RelayState");
            if (state == null || !state.matches("[A-Za-z0-9]{32}")) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "SAML RelayState 无效");
                return;
            }
            String key = "saml:login:state:" + state;
            String tenantId = redis.execute(CONSUME_STATE, java.util.Collections.singletonList(key));
            if (tenantId == null || !tenantId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "SAML RelayState 已过期");
                return;
            }
            OidcIdentityBinding binding = mapper.findBoundIdentity(tenantId, authentication);
            if (binding == null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "SAML 身份尚未绑定本地用户");
                return;
            }
            UserVo user = userService.loginByIdentity(binding.getUserId());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getWriter(), WebResponse.OK(user));
        };
    }
}
