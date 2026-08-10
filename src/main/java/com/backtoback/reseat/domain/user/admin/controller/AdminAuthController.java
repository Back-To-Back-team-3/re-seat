package com.backtoback.reseat.domain.user.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backtoback.reseat.domain.user.admin.dto.request.AdminLoginRequest;
import com.backtoback.reseat.domain.user.admin.dto.response.AdminLoginResponse;
import com.backtoback.reseat.domain.user.admin.service.AdminAuthService;
import com.backtoback.reseat.global.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AdminLoginResponse>> login(@Valid @RequestBody
    AdminLoginRequest request) {
        AdminLoginResponse response = adminAuthService.login(request);
        return ResponseEntity.ok(ApiResponse.success("관리자 로그인 성공 완료", response));
    }
}
