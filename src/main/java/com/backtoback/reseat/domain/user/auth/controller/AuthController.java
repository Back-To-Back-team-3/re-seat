package com.backtoback.reseat.domain.user.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.backtoback.reseat.domain.user.auth.dto.request.ReissueRequest;
import com.backtoback.reseat.domain.user.auth.dto.request.UserLoginRequest;
import com.backtoback.reseat.domain.user.auth.dto.response.TokenResponse;
import com.backtoback.reseat.domain.user.auth.service.AuthService;
import com.backtoback.reseat.global.common.ApiResponse;
import com.backtoback.reseat.global.exception.BusinessException;
import com.backtoback.reseat.global.exception.ErrorCode;
import com.backtoback.reseat.global.security.CustomUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthControllerDocs {

    private final AuthService authService;

    @PostMapping("/login")
    @Override
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody UserLoginRequest request) {
        TokenResponse response = authService.login(request);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("로그인 성공 완료", response));
    }

    @PostMapping("/reissue")
    @Override
    public ResponseEntity<ApiResponse<TokenResponse>> reissue(@Valid @RequestBody ReissueRequest request) {
        TokenResponse response = authService.reissue(request);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("토큰 재발급 완료", response));
    }

    @PostMapping("/logout")
    @Override
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        authService.logout(userDetails.getId());
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("로그아웃 완료", null));
    }
}
