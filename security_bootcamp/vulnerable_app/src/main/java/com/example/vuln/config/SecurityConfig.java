package com.example.vuln.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 학습용 보안 설정.
 *
 * 의도적으로 일부 보호를 약하게 두어 /vuln/* 공격 실습이 가능하게 한 뒤,
 * 학생이 직접 강화해 가도록 한다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 학습용: CSRF를 끄지 않고 vuln 경로는 무시하는 식으로 부분 비활성화
            .csrf(c -> c.ignoringRequestMatchers("/vuln/**"))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/", "/login", "/h2-console/**",
                                 "/css/**", "/js/**").permitAll()
                .requestMatchers("/vuln/**", "/safe/**").permitAll() // 학습 편의를 위해 일부 공개
                .anyRequest().authenticated()
            )
            .formLogin(Customizer.withDefaults())
            .logout(Customizer.withDefaults())
            .sessionManagement(s -> s
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation().migrateSession()
            )
            // H2 콘솔 frame을 위해 학습용으로 sameorigin
            .headers(h -> h
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
