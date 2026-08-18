package com.backtoback.reseat.domain.user.verification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backtoback.reseat.domain.user.verification.dto.request.VerificationRequest;
import com.backtoback.reseat.domain.user.verification.dto.response.VerificationStatusResponse;
import com.backtoback.reseat.domain.user.verification.service.VerificationService;
import com.backtoback.reseat.global.common.ApiResponse;
import com.backtoback.reseat.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationService verificationService;
    //인증여부 상태 조회
    @GetMapping("/users/verification/status")
    public ResponseEntity<ApiResponse<VerificationStatusResponse>> getVerificationStatus(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        VerificationStatusResponse response = verificationService.getVerificationStatus(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("본인인증 상태 조회 완료", response));
    }

    @PostMapping("/users/verification")
    public ResponseEntity<ApiResponse<Void>> verifyIdentity(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @RequestBody VerificationRequest request
    ) {
        verificationService.verifyAndUpdateUser(userDetails.getId(), request.getImpUid());
        return ResponseEntity.ok(ApiResponse.success("본인인증 완료", null));
    }
}
