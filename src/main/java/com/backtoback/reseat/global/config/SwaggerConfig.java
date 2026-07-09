package com.backtoback.reseat.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        String securityJwtName = "JWT Bearer Token";
        String queueTokenName = "Queue-Token";

        // Swagger UI가 전역 API 요청 시 둘 다 인증 헤더로 요구할 수 있도록 설정
        SecurityRequirement securityRequirement = new SecurityRequirement()
            .addList(securityJwtName)
            .addList(queueTokenName);

        // Security Schemes 컴포넌트 추가
        Components components = new Components()
            // 1. JWT Bearer Token 설정
            .addSecuritySchemes(securityJwtName, new SecurityScheme()
                .name(securityJwtName)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT"))
            // 2. Queue-Token API Key 헤더 설정 추가
            .addSecuritySchemes(queueTokenName, new SecurityScheme()
                .name("Queue-Token")
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .description("대기열을 통과한 후 발급받은 입장 토큰 (예: qt_eyJ...)"));

        return new OpenAPI()
            .info(new Info()
                .title("Re:Seat 야구장 예매 시스템 API")
                .description("Back-To-Back 팀 3조의 Re:Seat 백엔드 공용 API 명세서입니다.")
                .version("v1.0.0"))
            .addSecurityItem(securityRequirement)
            .components(components);
    }
}
