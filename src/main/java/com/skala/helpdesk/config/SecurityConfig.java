package com.skala.helpdesk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Phase 7 — 최소 인증·인가. 이 캡스톤은 학습용 단일 서비스라 회원가입·JWT 없이 HTTP Basic +
 * 인메모리 계정 2개로 {@code Principal}을 확보하는 데 집중한다. {@code /api/admin/**}만
 * ADMIN 롤을 요구한다 — 모델이 도구로 닿지 못하는 것과 별개로, 사람 중 아무나 승인 버튼을
 * 누를 수 있으면 안 된다.
 *
 * <p>기본 계정: {@code user1/user1234}(USER), {@code admin1/admin1234}(USER+ADMIN).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder encoder) {
        var user1 = User.withUsername("user1")
                .password(encoder.encode("user1234"))
                .roles("USER")
                .build();
        var admin1 = User.withUsername("admin1")
                .password(encoder.encode("admin1234"))
                .roles("USER", "ADMIN")
                .build();
        return new InMemoryUserDetailsManager(user1, admin1);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // 세션 쿠키가 아니라 Basic 인증을 쓰는 REST API
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)) // H2 콘솔
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/h2-console/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .httpBasic(withDefaults());
        return http.build();
    }
}
