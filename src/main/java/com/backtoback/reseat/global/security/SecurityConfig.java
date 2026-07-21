//securityConfig
package com.backtoback.reseat.global.security;

import com.backtoback.reseat.domain.user.auth.service.CustomOAuth2UserService;
import com.backtoback.reseat.domain.user.auth.service.OAuth2AuthenticationFailureHandler;
import com.backtoback.reseat.domain.user.auth.service.OAuth2AuthenticationSuccessHandler;
import jakarta.servlet.DispatcherType;
import org.springframework.http.HttpMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final CustomOAuth2UserService   customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            //CORS  기본 설정 적용
            .cors(Customizer.withDefaults())
            //CRSF 및 기본 로그인 폼 비활성화
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            //세션 정책 무상태 설정
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            //엔드 포인트별 인가 허용 정책 설정
            .authorizeHttpRequests(auth -> auth

                // 비동기 요청 처리 후 발생하는 ASYNC 디스패치를 허용한다.
                .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()

                //인증이 불필요한 경로 허용
                .requestMatchers("/api/v1/auth/**", "/oauth2/**", "/login/**").permitAll()
                .requestMatchers(PathRequest.toH2Console()).permitAll()
                .requestMatchers("/swagger-ui/**","/v3/api-docs/**").permitAll()

                //경기 상세 목록 조회 GET API 공개 허용
                .requestMatchers(HttpMethod.GET, "/api/v1/games", "/api/v1/games/*").permitAll()

                    //관리자 전용 API 인가 적용
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            // OAuth2 로그인 설정 추가
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                )
                .successHandler(oAuth2AuthenticationSuccessHandler)
                .failureHandler(oAuth2AuthenticationFailureHandler)
            )
            //Spring Security 필터 단의 예외(401, 403)
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )

            // 4. H2 콘솔의 iframe 사용 허용을 위한 X-Frame-Options 설정
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.sameOrigin())
                )

                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    //프론트엔드 연동대비
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

            configuration.setAllowedOrigins(List.of(
                "http://localhost:3000",             // React 개발용 로컬 주소
                "http://localhost:5173",             // Vite 개발용 로컬 주소
                "https://re-seat.netlify.app",        // 프론트엔드 임시/배포 주소 예시
                "https://your-frontend-domain.com"   // 실서버 프론트엔드 도메인 (필요시 추가)
            ));

        configuration.addAllowedMethod("*");      // GET, POST, PUT, DELETE 등 전체 허용
        configuration.addAllowedHeader("*");      // Authorization, Queue-Token, Idempotency-Key 등 허용
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);           // Preflight 요청 캐싱 시간

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
