package com.backtoback.reseat.domain.user.auth.controller;

import com.backtoback.reseat.domain.user.auth.dto.request.ReissueRequest;
import com.backtoback.reseat.domain.user.auth.dto.request.UserLoginRequest;
import com.backtoback.reseat.domain.user.auth.dto.response.TokenResponse;
import com.backtoback.reseat.domain.user.auth.service.AuthService;
import com.backtoback.reseat.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthControllerDocs {

    private final AuthService authService;

    @PostMapping("/login")
    @Override
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody UserLoginRequest request) {
        TokenResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("로그인 성공 완료", response));
    }

    @PostMapping("/reissue")
    @Override
    public ResponseEntity<ApiResponse<TokenResponse>> reissue(@Valid @RequestBody ReissueRequest request) {
        TokenResponse response = authService.reissue(request);
        return ResponseEntity.ok(ApiResponse.success("토큰 재발급 완료", response));
    }
}
