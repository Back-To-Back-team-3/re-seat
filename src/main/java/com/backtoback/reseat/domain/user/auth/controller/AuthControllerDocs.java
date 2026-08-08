package com.backtoback.reseat.domain.user.auth.controller;

import com.backtoback.reseat.domain.user.auth.dto.request.ReissueRequest;
import com.backtoback.reseat.domain.user.auth.dto.request.UserLoginRequest;
import com.backtoback.reseat.domain.user.auth.dto.response.TokenResponse;
import com.backtoback.reseat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Auth", description = "사용자 인증 · 로그인 · 토큰 관리 API")
public interface AuthControllerDocs {

    @Operation(
        summary = "일반 사용자 로그인",
        description = "이메일과 비밀번호로 로그인하여 Access Token과 Refresh Token을 발급받습니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "로그인 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "INVALID_REQUEST / INVALID_PASSWORD",
            content = @Content
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "USER_NOT_FOUND",
            content = @Content
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "USER_INACTIVE / SUSPENDED_USER",
            content = @Content
        )
    })
    ResponseEntity<ApiResponse<TokenResponse>> login(UserLoginRequest request);

    @Operation(
        summary = "토큰 재발급",
        description = "만료된 Access Token 대신 Refresh Token으로 새로운 토큰 쌍을 발급받습니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "토큰 재발급 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "INVALID_TOKEN",
            content = @Content
        )
    })
    ResponseEntity<ApiResponse<TokenResponse>> reissue(ReissueRequest request);
}
