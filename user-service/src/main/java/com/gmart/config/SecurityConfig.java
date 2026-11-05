package com.gmart.config;

import com.gmart.security.SecuritySupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SecuritySupport support) throws Exception {
        return support.apply(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/internal/**", "/error", "/h2-console/**", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/auth/login", "/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/contact").permitAll()
                        .requestMatchers("/employees/**", "/contact/all", "/users/**").hasAuthority("ROLE_ADMIN")
                        .anyRequest().authenticated())
                .build();
    }
}
