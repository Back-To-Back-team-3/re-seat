package com.backtoback.reseat.domain.user.verification.controller;

import com.backtoback.reseat.domain.user.verification.dto.request.VerificationRequest;
import com.backtoback.reseat.domain.user.verification.service.VerificationService;
import com.backtoback.reseat.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationService verificationService;

    @PostMapping("/verification")
    public ResponseEntity<Void> verifyIdentity(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @RequestBody VerificationRequest request
    ) {
        verificationService.verifyAndUpdateUser(userDetails.getId(), request.getImpUid());
        return ResponseEntity.ok().build();
    }
}
