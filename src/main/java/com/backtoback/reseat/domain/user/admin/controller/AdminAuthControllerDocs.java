package com.backtoback.reseat.domain.user.admin.controller;

import org.springframework.http.ResponseEntity;

import com.backtoback.reseat.domain.user.admin.dto.request.AdminLoginRequest;
import com.backtoback.reseat.domain.user.admin.dto.response.AdminLoginResponse;
import com.backtoback.reseat.global.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
    name = "Admin - Auth",
    description = "관리자 인증 · 로그인 API"
)
public interface AdminAuthControllerDocs {

    @Operation(
        summary = "관리자 로그인",
        description = "관리자 계정 이메일과 비밀번호로 로그인하여 Access Token과 Refresh Token을 발급받습니다. (ROLE_ADMIN 전용)"
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "관리자 로그인 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "INVALID_REQUEST / INVALID_PASSWORD",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "ADMIN_ACCESS_REQUIRED / USER_INACTIVE",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "USER_NOT_FOUND",
                content = @Content
            )
        }
    )
    ResponseEntity<ApiResponse<AdminLoginResponse>> login(AdminLoginRequest request);
}
