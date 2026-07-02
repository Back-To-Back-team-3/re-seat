package com.backtoback.reseat.global.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

   @Bean
    public PasswordEncoder passwordEncoder(){
       return new BCryptPasswordEncoder();
   }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. POST 요청을 보낼 수 있도록 CSRF 보안을 잠시 끕니다 (API 서버 필수)
                .csrf(csrf -> csrf.disable())

                // 2. 인증(로그인) 없이 통과할 수 있는 경로를 지정합니다
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()

                        // 그 외의 모든 API 요청은 인증이 필요함
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
