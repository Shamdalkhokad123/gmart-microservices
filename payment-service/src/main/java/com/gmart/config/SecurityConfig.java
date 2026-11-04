package com.gmart.config;

import com.gmart.security.SecuritySupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SecuritySupport support) throws Exception {
        return support.apply(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/internal/**", "/error", "/h2-console/**", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Razorpay webhook: verified by HMAC signature, not JWT
                        .requestMatchers(HttpMethod.POST, "/payments/razorpay/webhook").permitAll()
                        .anyRequest().authenticated())
                .build();
    }
}
