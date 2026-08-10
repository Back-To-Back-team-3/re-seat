//회원가입, 내 정보 조회 API

package com.backtoback.reseat.domain.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backtoback.reseat.domain.user.dto.request.PasswordChangeRequest;
import com.backtoback.reseat.domain.user.dto.request.UserSignUpRequest;
import com.backtoback.reseat.domain.user.dto.request.UserUpdateRequest;
import com.backtoback.reseat.domain.user.dto.response.UserProfileResponse;
import com.backtoback.reseat.domain.user.dto.response.UserSignUpResponse;
import com.backtoback.reseat.domain.user.service.UserService;
import com.backtoback.reseat.global.common.ApiResponse;
import com.backtoback.reseat.global.security.CustomUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/auth/signup")
    public ResponseEntity<ApiResponse<UserSignUpResponse>> signup(@Valid @RequestBody
    UserSignUpRequest request) {
        //비즈니스 로직 수행 후 가입된 유저의 PK ID 확보
        UserSignUpResponse response = userService.signUp(request);

        //공통 응답 포맷인 ApiResponse.success에 ID를 담아 반환
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("회원가입 완료", response));

    }

    @GetMapping("/users/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
        @AuthenticationPrincipal
        CustomUserDetails customUser) {

        UserProfileResponse response = userService.getMyProfile(customUser.getId());
        return ResponseEntity
            .status(HttpStatus.OK)
            .body(ApiResponse.success("내 정보 조회 완료", response));
    }

    @PutMapping("/users/me")
    public ResponseEntity<ApiResponse<Void>> updateMyProfile(
        @AuthenticationPrincipal
        CustomUserDetails customUser,
        @Valid @RequestBody
        UserUpdateRequest request) {
        userService.updateProfile(customUser.getId(), request);
        return ResponseEntity
            .status(HttpStatus.OK)
            .body(ApiResponse.success("회원정보 수정 완료", null));
    }

    @PatchMapping("/users/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
        @AuthenticationPrincipal
        CustomUserDetails customUser,
        @Valid @RequestBody
        PasswordChangeRequest request) {

        userService.changePassword(customUser.getId(), request);
        return ResponseEntity
            .status(HttpStatus.OK)
            .body(ApiResponse.success("비밀번호 변경 완료", null));
    }

    @DeleteMapping("/users/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(
        @AuthenticationPrincipal
        CustomUserDetails customUser) {
        userService.withdraw(customUser.getId());

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(ApiResponse.success("회원 탈퇴 완료", null));
    }
}
