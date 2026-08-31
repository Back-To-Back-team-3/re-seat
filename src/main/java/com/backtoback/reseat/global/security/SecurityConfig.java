//securityConfig
package com.backtoback.reseat.global.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.backtoback.reseat.domain.user.auth.service.CustomOAuth2UserService;
import com.backtoback.reseat.domain.user.auth.service.OAuth2AuthenticationFailureHandler;
import com.backtoback.reseat.domain.user.auth.service.OAuth2AuthenticationSuccessHandler;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CORS 기본 설정 적용
            .cors(Customizer.withDefaults())
            // CRSF 및 기본 로그인 폼 비활성화
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            // 세션 정책 무상태 설정
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 엔드 포인트별 인가 허용 정책 설정
            .authorizeHttpRequests(
                auth -> auth

                    // 헬스체크 및 모니터링 경로 허용
                    .requestMatchers("/actuator", "/actuator/**")
                    .permitAll()

                    // 비동기 요청 처리 후 발생하는 ASYNC 디스패치를 허용한다.
                    .dispatcherTypeMatchers(DispatcherType.ASYNC)
                    .permitAll()

                    // 로그아웃 요청은 인증 필요
                    .requestMatchers("/api/v1/auth/logout")
                    .authenticated()

                    // 인증이 불필요한 경로 허용
                    .requestMatchers("/api/v1/auth/**", "/oauth2/**", "/login/**")
                    .permitAll()
                    .requestMatchers("/h2-console/**")
                    .permitAll()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**")
                    .permitAll()

                    // 경기 목록(/api/v1/games), 경기 상세(/api/v1/games/{gameId}) GET 공개 허용
                    .requestMatchers(HttpMethod.GET, "/api/v1/games", "/api/v1/games/{gameId}")
                    .permitAll()

                    // 관리자 전용 API 인가 적용
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated()
            )
            // OAuth2 로그인 설정 추가
            .oauth2Login(
                oauth2 -> oauth2
                    .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                    .successHandler(oAuth2AuthenticationSuccessHandler)
                    .failureHandler(oAuth2AuthenticationFailureHandler)
            )
            // Spring Security 필터 단의 예외(401, 403)
            .exceptionHandling(
                exception -> exception
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
            )

            // 4. H2 콘솔의 iframe 사용 허용을 위한 X-Frame-Options 설정
            .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))

            .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 프론트엔드 연동대비 CORS 설정 (환경변수 주입)
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
        @Value("${cors.allowed-origins}") List<String> allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "Queue-Token", "Location"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // Preflight 요청 캐싱 시간

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
