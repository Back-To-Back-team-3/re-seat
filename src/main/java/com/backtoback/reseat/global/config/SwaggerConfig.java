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
    public OpenAPI openAPI(){
        String securityJwtName = "JWT Bearer Token";
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(securityJwtName);

        // Swagger UI 우측 상단에 "Authorize" 버튼을 만들고 Bearer JWT 토큰을 입력할 수 있게 하는 컴포넌트
        Components components = new Components().addSecuritySchemes(securityJwtName, new SecurityScheme()
            .name(securityJwtName)
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT"));

        return new OpenAPI()
            .info(new Info()
                .title("Re:Seat 야구장 예매 시스템 API")
                .description("Back-To-Back 팀 3조의 Re:Seat 백엔드 공용 API 명세서입니다.")
                .version("v1.0.0"))
            .addSecurityItem(securityRequirement)
            .components(components);

    }
}
