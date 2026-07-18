package com.backtoback.reseat.domain.verification.controller;

import com.backtoback.reseat.domain.verification.dto.request.VerificationRequest;
import com.backtoback.reseat.domain.verification.service.VerificationService;
import com.backtoback.reseat.global.common.ApiResponse;
import com.backtoback.reseat.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationService verificationService;

    @PostMapping("/users/verification")
    public ResponseEntity<ApiResponse<Void>> verifyIdentity(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody VerificationRequest request
    ) {
        verificationService.verifyAndUpdateUser(userDetails.getId(), request.getImpUid());
        return ResponseEntity.ok(ApiResponse.success("본인인증 완료", null));
    }
}
