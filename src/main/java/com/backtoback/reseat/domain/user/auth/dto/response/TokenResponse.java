package com.backtoback.reseat.domain.user.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "인증 토큰 응답")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {

    @Schema(description = "인증 타입", example = "Bearer")
    @Builder.Default
    private String grantType = "Bearer";

    @Schema(description = "Access Token", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String accessToken;

    @Schema(description = "Refresh Token", example = "dGhpcy1pcy1hLXJlZnJlc2gtdG9rZW4...")
    private String refreshToken;

}
