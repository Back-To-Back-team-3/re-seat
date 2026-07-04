//회원가입, 내 정보 조회 API

package com.backtoback.reseat.domain.user.controller;

import com.backtoback.reseat.domain.user.dto.request.UserLoginRequest;
import com.backtoback.reseat.domain.user.dto.request.UserSignUpRequest;
import com.backtoback.reseat.domain.user.dto.response.TokenResponse;
import com.backtoback.reseat.domain.user.dto.response.UserSignUpResponse;
import com.backtoback.reseat.domain.user.service.UserService;
import com.backtoback.reseat.domain.user.service.AuthService;
import com.backtoback.reseat.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthService authService;
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserSignUpResponse>> signup(@Valid @RequestBody UserSignUpRequest request){
        //비즈니스 로직 수행 후 가입된 유저의 PK ID 확보
        UserSignUpResponse response = userService.signUp(request);

        //공통 응답 포맷인 ApiResponse.success에 ID를 담아 반환
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("회원가입 완료", response));

    }

}
