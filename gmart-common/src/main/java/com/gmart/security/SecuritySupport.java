package com.gmart.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;

/**
 * The parts of the security setup every service shares. Each service's own
 * SecurityConfig calls {@link #apply} and then adds its URL rules.
 * CORS is handled once, at the API gateway.
 */
@Component
public class SecuritySupport {

    private final JwtTokenProvider tokenProvider;
    private final String internalToken;

    public SecuritySupport(JwtTokenProvider tokenProvider,
                           @Value("${gmart.internal.token}") String internalToken) {
        this.tokenProvider = tokenProvider;
        this.internalToken = internalToken;
    }

    public HttpSecurity apply(HttpSecurity http) throws Exception {
        return http
                .cors(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .addFilterBefore(new InternalApiFilter(internalToken), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class);
    }
}
